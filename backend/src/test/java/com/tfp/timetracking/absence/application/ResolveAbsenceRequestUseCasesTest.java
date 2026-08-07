package com.tfp.timetracking.absence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.absence.domain.AbsenceRequest;
import com.tfp.timetracking.absence.domain.AbsenceRequestRepository;
import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResolveAbsenceRequestUseCasesTest {

    @Test
    void approveRejectAndCancelPersistAndPublish() {
        AbsenceRequestRepository repository = org.mockito.Mockito.mock(AbsenceRequestRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        Clock clock = () -> Instant.parse("2026-08-01T10:00:00Z");
        IdGenerator idGenerator = UUID::randomUUID;
        DomainEventPublisher publisher = org.mockito.Mockito.mock(DomainEventPublisher.class);
        AuditRecorder auditRecorder = org.mockito.Mockito.mock(AuditRecorder.class);

        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID resolverId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(resolverId);

        AbsenceRequest approvalTarget = request(requestId, tenantId, employeeId, typeId);
        when(repository.findById(tenantId, requestId)).thenReturn(java.util.Optional.of(approvalTarget));
        when(repository.save(any(AbsenceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AbsenceRequest approved = new ApproveAbsenceRequestUseCase(
                        repository, tenantContext, clock, idGenerator, publisher, auditRecorder)
                .approve(new ResolveAbsenceCommand(requestId, "Ok"));
        assertThat(approved.status().name()).isEqualTo("APPROVED");

        AbsenceRequest rejectionTarget = request(requestId, tenantId, employeeId, typeId);
        when(repository.findById(tenantId, requestId)).thenReturn(java.util.Optional.of(rejectionTarget));
        AbsenceRequest rejected = new RejectAbsenceRequestUseCase(
                        repository, tenantContext, clock, idGenerator, publisher, auditRecorder)
                .reject(new ResolveAbsenceCommand(requestId, "No"));
        assertThat(rejected.status().name()).isEqualTo("REJECTED");

        when(tenantContext.currentUserId()).thenReturn(employeeId);
        AbsenceRequest cancelTarget = request(requestId, tenantId, employeeId, typeId);
        when(repository.findById(tenantId, requestId)).thenReturn(java.util.Optional.of(cancelTarget));
        AbsenceRequest cancelled = new CancelAbsenceRequestUseCase(repository, tenantContext, clock, idGenerator, publisher)
                .cancel(requestId);
        assertThat(cancelled.status().name()).isEqualTo("CANCELLED");

        verify(repository, atLeast(3)).save(any(AbsenceRequest.class));
        verify(publisher, atLeast(3)).publish(any());
        verify(auditRecorder, atLeast(2)).record(any(), any(), any(), any());
    }

    private AbsenceRequest request(UUID requestId, UUID tenantId, UUID employeeId, UUID typeId) {
        return AbsenceRequest.reconstitute(
                requestId,
                tenantId,
                employeeId,
                typeId,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                "Vacaciones",
                com.tfp.timetracking.absence.domain.AbsenceRequestStatus.PENDING,
                null,
                null,
                null,
                Instant.parse("2026-08-01T10:00:00Z"));
    }
}
