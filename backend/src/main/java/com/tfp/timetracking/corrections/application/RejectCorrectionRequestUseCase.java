package com.tfp.timetracking.corrections.application;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RejectCorrectionRequestUseCase {

    private final CorrectionRequestRepository correctionRequestRepository;
    private final TenantContext tenantContext;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditRecorder auditRecorder;

    public RejectCorrectionRequestUseCase(
            CorrectionRequestRepository correctionRequestRepository,
            TenantContext tenantContext,
            Clock clock,
            IdGenerator idGenerator,
            DomainEventPublisher domainEventPublisher,
            AuditRecorder auditRecorder) {
        this.correctionRequestRepository = correctionRequestRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.domainEventPublisher = domainEventPublisher;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public CorrectionRequest reject(ResolveCorrectionCommand command) {
        var tenantId = tenantContext.currentTenantId();
        var actorUserId = tenantContext.currentUserId();
        CorrectionRequest correction = correctionRequestRepository.findById(tenantId, command.correctionId())
                .orElseThrow(() -> new ResourceNotFoundException("Correccion no encontrada"));
        correction.reject(actorUserId, command.resolutionComment(), clock.now(), idGenerator);
        CorrectionRequest saved = correctionRequestRepository.save(correction);
        domainEventPublisher.publish(correction.pullDomainEvents());
        auditRecorder.record(
                "CORRECTION_REJECTED",
                "CorrectionRequest",
                saved.id(),
                Map.of("workdayId", saved.workdayId().toString(), "resolvedBy", actorUserId.toString()));
        return saved;
    }
}
