package com.tfp.timetracking.absence.application;

import com.tfp.timetracking.absence.domain.AbsenceRequest;
import com.tfp.timetracking.absence.domain.AbsenceRequestRepository;
import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RejectAbsenceRequestUseCase {

    private final AbsenceRequestRepository absenceRequestRepository;
    private final TenantContext tenantContext;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditRecorder auditRecorder;

    public RejectAbsenceRequestUseCase(
            AbsenceRequestRepository absenceRequestRepository,
            TenantContext tenantContext,
            Clock clock,
            IdGenerator idGenerator,
            DomainEventPublisher domainEventPublisher,
            AuditRecorder auditRecorder) {
        this.absenceRequestRepository = absenceRequestRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.domainEventPublisher = domainEventPublisher;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public AbsenceRequest reject(ResolveAbsenceCommand command) {
        AbsenceRequest request = absenceRequestRepository.findById(tenantContext.currentTenantId(), command.absenceRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de ausencia no encontrada"));
        request.reject(tenantContext.currentUserId(), command.resolutionComment(), clock.now(), idGenerator);
        AbsenceRequest saved = absenceRequestRepository.save(request);
        domainEventPublisher.publish(request.pullDomainEvents());
        auditRecorder.record(
                "ABSENCE_REJECTED",
                "AbsenceRequest",
                saved.id(),
                Map.of("employeeId", saved.employeeId().toString(), "absenceTypeId", saved.absenceTypeId().toString()));
        return saved;
    }
}
