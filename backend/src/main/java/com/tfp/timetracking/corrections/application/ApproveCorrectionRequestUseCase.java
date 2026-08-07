package com.tfp.timetracking.corrections.application;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.timetracking.application.EvaluateClosedWorkdayService;
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
    private final EvaluateClosedWorkdayService evaluateClosedWorkdayService;
    private final AuditRecorder auditRecorder;

    public ApproveCorrectionRequestUseCase(
            CorrectionRequestRepository correctionRequestRepository,
            WorkdayRepository workdayRepository,
            TenantContext tenantContext,
            Clock clock,
            IdGenerator idGenerator,
            DomainEventPublisher domainEventPublisher,
            EvaluateClosedWorkdayService evaluateClosedWorkdayService,
            AuditRecorder auditRecorder) {
        this.correctionRequestRepository = correctionRequestRepository;
        this.workdayRepository = workdayRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.domainEventPublisher = domainEventPublisher;
        this.evaluateClosedWorkdayService = evaluateClosedWorkdayService;
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

        // La correccion se resuelve ANTES de tocar la jornada. Al reves, una
        // segunda aprobacion chocaba con la invariante de la jornada y devolvia
        // WORKDAY_ALREADY_CLOSED, que describe un sintoma y no la causa: lo que
        // ocurre es que esa correccion ya estaba resuelta. Ambas operaciones
        // comparten transaccion, asi que el orden no afecta a la atomicidad.
        correction.approve(actorUserId, command.resolutionComment(), clock.now(), idGenerator);
        workday.adjust(correction.proposedChanges().toWorkdayAdjustment(), clock.now(), idGenerator);

        Workday savedWorkday = workdayRepository.save(workday);
        CorrectionRequest saved = correctionRequestRepository.save(correction);
        List<Object> events = mergeEvents(workday.pullDomainEvents(), correction.pullDomainEvents());
        events.addAll(evaluateClosedWorkdayService.evaluate(savedWorkday));
        domainEventPublisher.publish(events);
        // Dos entradas de auditoría, no una: quien investiga qué le pasó a una
        // jornada busca por la jornada, y no tiene por qué saber que el cambio
        // vino de una corrección. Sin esta segunda entrada, el ajuste no
        // aparecía en la auditoría de la entidad modificada (T130-04).
        auditRecorder.record(
                "WORKDAY_ADJUSTED",
                "Workday",
                savedWorkday.id(),
                Map.of(
                        "correctionId", saved.id().toString(),
                        "startedAt", String.valueOf(savedWorkday.startedAt()),
                        "endedAt", String.valueOf(savedWorkday.endedAt()),
                        "adjustedBy", actorUserId.toString()));
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
