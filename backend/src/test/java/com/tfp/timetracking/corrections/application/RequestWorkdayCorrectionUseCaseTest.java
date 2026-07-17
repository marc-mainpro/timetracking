package com.tfp.timetracking.corrections.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.corrections.domain.CorrectionAlreadyPendingException;
import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.corrections.domain.ProposedChanges;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestWorkdayCorrectionUseCaseTest {

    @Test
    void createsCorrectionForOwnWorkday() {
        CorrectionRequestRepository correctionRequestRepository = org.mockito.Mockito.mock(CorrectionRequestRepository.class);
        WorkdayRepository workdayRepository = org.mockito.Mockito.mock(WorkdayRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        DomainEventPublisher publisher = org.mockito.Mockito.mock(DomainEventPublisher.class);
        Clock clock = () -> Instant.parse("2026-01-16T09:00:00Z");
        IdGenerator idGenerator = UUID::randomUUID;
        RequestWorkdayCorrectionUseCase useCase = new RequestWorkdayCorrectionUseCase(
                correctionRequestRepository, workdayRepository, tenantContext, clock, idGenerator, publisher);
        Workday workday = Workday.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                com.tfp.timetracking.timetracking.domain.WorkdayStatus.CLOSED,
                Instant.parse("2026-01-15T09:00:00Z"),
                Instant.parse("2026-01-15T18:00:00Z"),
                0L,
                Instant.parse("2026-01-15T09:00:00Z"),
                Instant.parse("2026-01-15T18:00:00Z"),
                List.of());
        when(tenantContext.currentTenantId()).thenReturn(workday.tenantId());
        when(tenantContext.currentUserId()).thenReturn(workday.employeeId());
        when(workdayRepository.findById(workday.tenantId(), workday.id())).thenReturn(java.util.Optional.of(workday));
        when(correctionRequestRepository.findPendingByWorkdayAndRequestedBy(workday.tenantId(), workday.id(), workday.employeeId()))
                .thenReturn(java.util.Optional.empty());
        when(correctionRequestRepository.save(any(CorrectionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CorrectionRequest result = useCase.request(new RequestWorkdayCorrectionCommand(workday.id(), "Ajuste", validChanges()));

        assertThat(result.status().name()).isEqualTo("PENDING");
        verify(publisher).publish(any(List.class));
    }

    @Test
    void rejectsPendingDuplicate() {
        CorrectionRequestRepository correctionRequestRepository = org.mockito.Mockito.mock(CorrectionRequestRepository.class);
        WorkdayRepository workdayRepository = org.mockito.Mockito.mock(WorkdayRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        RequestWorkdayCorrectionUseCase useCase = new RequestWorkdayCorrectionUseCase(
                correctionRequestRepository, workdayRepository, tenantContext, () -> Instant.now(), UUID::randomUUID,
                org.mockito.Mockito.mock(DomainEventPublisher.class));
        UUID tenantId = UUID.randomUUID();
        UUID workdayId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Workday workday = Workday.reconstitute(
                workdayId, tenantId, userId, com.tfp.timetracking.timetracking.domain.WorkdayStatus.CLOSED,
                Instant.now(), Instant.now().plusSeconds(60), 0L, Instant.now(), Instant.now(), List.of());
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(userId);
        when(workdayRepository.findById(tenantId, workdayId)).thenReturn(java.util.Optional.of(workday));
        when(correctionRequestRepository.findPendingByWorkdayAndRequestedBy(tenantId, workdayId, userId))
                .thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(CorrectionRequest.class)));

        assertThatThrownBy(() -> useCase.request(new RequestWorkdayCorrectionCommand(workdayId, "dup", validChanges())))
                .isInstanceOf(CorrectionAlreadyPendingException.class);
    }

    @Test
    void rejectsForeignWorkdayAsNotFound() {
        CorrectionRequestRepository correctionRequestRepository = org.mockito.Mockito.mock(CorrectionRequestRepository.class);
        WorkdayRepository workdayRepository = org.mockito.Mockito.mock(WorkdayRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        RequestWorkdayCorrectionUseCase useCase = new RequestWorkdayCorrectionUseCase(
                correctionRequestRepository, workdayRepository, tenantContext, () -> Instant.now(), UUID::randomUUID,
                org.mockito.Mockito.mock(DomainEventPublisher.class));
        UUID tenantId = UUID.randomUUID();
        UUID workdayId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        Workday workday = Workday.reconstitute(
                workdayId, tenantId, ownerId, com.tfp.timetracking.timetracking.domain.WorkdayStatus.CLOSED,
                Instant.now(), Instant.now().plusSeconds(60), 0L, Instant.now(), Instant.now(), List.of());
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(currentUserId);
        when(workdayRepository.findById(tenantId, workdayId)).thenReturn(java.util.Optional.of(workday));

        assertThatThrownBy(() -> useCase.request(new RequestWorkdayCorrectionCommand(workdayId, "forbidden", validChanges())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private ProposedChanges validChanges() {
        return new ProposedChanges(
                Instant.parse("2026-01-15T09:00:00Z"),
                Instant.parse("2026-01-15T18:00:00Z"),
                List.of(new ProposedChanges.ProposedBreak(
                        Instant.parse("2026-01-15T12:00:00Z"), Instant.parse("2026-01-15T12:30:00Z"))));
    }
}
