package com.tfp.timetracking.corrections.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.corrections.domain.CorrectionRequestStatus;
import com.tfp.timetracking.corrections.domain.ProposedChanges;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RejectCorrectionRequestUseCaseTest {

    @Test
    void rejectsPendingCorrectionAndAudits() {
        CorrectionRequestRepository repository = org.mockito.Mockito.mock(CorrectionRequestRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        DomainEventPublisher publisher = org.mockito.Mockito.mock(DomainEventPublisher.class);
        AuditRecorder auditRecorder = org.mockito.Mockito.mock(AuditRecorder.class);
        RejectCorrectionRequestUseCase useCase = new RejectCorrectionRequestUseCase(
                repository, tenantContext, () -> Instant.parse("2026-01-16T10:00:00Z"), UUID::randomUUID, publisher, auditRecorder);
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        CorrectionRequest correction = CorrectionRequest.reconstitute(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), UUID.randomUUID(), "reason", validChanges(),
                CorrectionRequestStatus.PENDING, null, null, null, 0L, Instant.parse("2026-01-16T09:00:00Z"));
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(actorId);
        when(repository.findById(tenantId, correction.id())).thenReturn(java.util.Optional.of(correction));
        when(repository.save(any(CorrectionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.reject(new ResolveCorrectionCommand(correction.id(), "rechazada"));

        verify(repository).save(any(CorrectionRequest.class));
        verify(publisher).publish(any(List.class));
        verify(auditRecorder).record(any(), any(), any(), any());
    }

    private ProposedChanges validChanges() {
        return new ProposedChanges(
                Instant.parse("2026-01-15T09:05:00Z"),
                Instant.parse("2026-01-15T18:05:00Z"),
                List.of(new ProposedChanges.ProposedBreak(
                        Instant.parse("2026-01-15T12:00:00Z"), Instant.parse("2026-01-15T12:30:00Z"))));
    }
}
