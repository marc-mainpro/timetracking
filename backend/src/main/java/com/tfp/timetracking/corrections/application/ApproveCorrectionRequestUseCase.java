package com.tfp.timetracking.corrections.application;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApproveCorrectionRequestUseCase {

    private final CorrectionRequestRepository correctionRequestRepository;
    private final WorkdayRepository workdayRepository;
    private final TenantContext tenantContext;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditRecorder auditRecorder;

    public ApproveCorrectionRequestUseCase(
            CorrectionRequestRepository correctionRequestRepository,
            WorkdayRepository workdayRepository,
            TenantContext tenantContext,
            Clock clock,
            IdGenerator idGenerator,
            DomainEventPublisher domainEventPublisher,
            AuditRecorder auditRecorder) {
        this.correctionRequestRepository = correctionRequestRepository;
        this.workdayRepository = workdayRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.domainEventPublisher = domainEventPublisher;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public CorrectionRequest approve(ResolveCorrectionCommand command) {
        var tenantId = tenantContext.currentTenantId();
        var actorUserId = tenantContext.currentUserId();
        CorrectionRequest correction = correctionRequestRepository.findById(tenantId, command.correctionId())
                .orElseThrow(() -> new ResourceNotFoundException("Correccion no encontrada"));
        Workday workday = workdayRepository.findById(tenantId, correction.workdayId())
                .orElseThrow(() -> new ResourceNotFoundException("Jornada no encontrada"));

        workday.adjust(correction.proposedChanges().toWorkdayAdjustment(), clock.now(), idGenerator);
        correction.approve(actorUserId, command.resolutionComment(), clock.now(), idGenerator);

        workdayRepository.save(workday);
        CorrectionRequest saved = correctionRequestRepository.save(correction);
        domainEventPublisher.publish(mergeEvents(workday.pullDomainEvents(), correction.pullDomainEvents()));
        auditRecorder.record(
                "CORRECTION_APPROVED",
                "CorrectionRequest",
                saved.id(),
                Map.of("workdayId", saved.workdayId().toString(), "resolvedBy", actorUserId.toString()));
        return saved;
    }

    private List<Object> mergeEvents(List<Object> first, List<Object> second) {
        List<Object> merged = new java.util.ArrayList<>(first);
        merged.addAll(second);
        return merged;
    }
}
