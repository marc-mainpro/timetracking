package com.tfp.timetracking.corrections.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.corrections.domain.CorrectionRequestStatus;
import com.tfp.timetracking.corrections.domain.ProposedChanges;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import com.tfp.timetracking.timetracking.domain.WorkdayStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApproveCorrectionRequestUseCaseTest {

    @Test
    void approvesCorrectionAndAdjustsWorkday() {
        CorrectionRequestRepository correctionRepository = org.mockito.Mockito.mock(CorrectionRequestRepository.class);
        WorkdayRepository workdayRepository = org.mockito.Mockito.mock(WorkdayRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        DomainEventPublisher publisher = org.mockito.Mockito.mock(DomainEventPublisher.class);
        AuditRecorder auditRecorder = org.mockito.Mockito.mock(AuditRecorder.class);
        Clock clock = () -> Instant.parse("2026-01-16T10:00:00Z");
        IdGenerator idGenerator = UUID::randomUUID;
        ApproveCorrectionRequestUseCase useCase = new ApproveCorrectionRequestUseCase(
                correctionRepository, workdayRepository, tenantContext, clock, idGenerator, publisher, auditRecorder);
        UUID tenantId = UUID.randomUUID();
        UUID resolverId = UUID.randomUUID();
        Workday workday = Workday.reconstitute(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), WorkdayStatus.CLOSED,
                Instant.parse("2026-01-15T09:00:00Z"), Instant.parse("2026-01-15T18:00:00Z"), 0L,
                Instant.parse("2026-01-15T09:00:00Z"), Instant.parse("2026-01-15T18:00:00Z"), List.of());
        CorrectionRequest correction = CorrectionRequest.reconstitute(
                UUID.randomUUID(), tenantId, workday.id(), workday.employeeId(), "ajuste", validChanges(),
                CorrectionRequestStatus.PENDING, null, null, null, Instant.parse("2026-01-16T09:00:00Z"));
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(resolverId);
        when(correctionRepository.findById(tenantId, correction.id())).thenReturn(java.util.Optional.of(correction));
        when(workdayRepository.findById(tenantId, workday.id())).thenReturn(java.util.Optional.of(workday));
        when(correctionRepository.save(any(CorrectionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workdayRepository.save(any(Workday.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.approve(new ResolveCorrectionCommand(correction.id(), "aprobada"));

        verify(workdayRepository).save(any(Workday.class));
        verify(correctionRepository).save(any(CorrectionRequest.class));
        verify(auditRecorder).record(any(), any(), any(), any());
    }

    @Test
    void rejectsMissingCorrection() {
        CorrectionRequestRepository correctionRepository = org.mockito.Mockito.mock(CorrectionRequestRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        ApproveCorrectionRequestUseCase useCase = new ApproveCorrectionRequestUseCase(
                correctionRepository,
                org.mockito.Mockito.mock(WorkdayRepository.class),
                tenantContext,
                () -> Instant.now(),
                UUID::randomUUID,
                org.mockito.Mockito.mock(DomainEventPublisher.class),
                org.mockito.Mockito.mock(AuditRecorder.class));
        UUID tenantId = UUID.randomUUID();
        UUID correctionId = UUID.randomUUID();
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(UUID.randomUUID());
        when(correctionRepository.findById(tenantId, correctionId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> useCase.approve(new ResolveCorrectionCommand(correctionId, "nope")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private ProposedChanges validChanges() {
        return new ProposedChanges(
                Instant.parse("2026-01-15T09:05:00Z"),
                Instant.parse("2026-01-15T18:05:00Z"),
                List.of(new ProposedChanges.ProposedBreak(
                        Instant.parse("2026-01-15T12:00:00Z"), Instant.parse("2026-01-15T12:30:00Z"))));
    }
}
