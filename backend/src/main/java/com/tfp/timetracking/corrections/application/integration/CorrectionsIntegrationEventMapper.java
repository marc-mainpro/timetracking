package com.tfp.timetracking.corrections.application.integration;

import com.tfp.timetracking.corrections.domain.event.CorrectionApproved;
import com.tfp.timetracking.corrections.domain.event.CorrectionRejected;
import com.tfp.timetracking.corrections.domain.event.CorrectionRequested;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.Map;
import java.util.Optional;

/**
 * Traduce eventos de dominio del modulo {@code corrections} a eventos de
 * integracion versionados (T702, CONTEXT-DOMINIO §4). Solo depende del
 * tipo {@link IntegrationEvent} de {@code shared.domain}, nunca de
 * infraestructura de outbox (ver {@code OutboxEncapsulationTest}).
 */
public final class CorrectionsIntegrationEventMapper {

    private static final String AGGREGATE_TYPE = "CorrectionRequest";

    private CorrectionsIntegrationEventMapper() {}

    /**
     * @param domainEvent evento de dominio recogido tras persistir el agregado
     * @return el evento de integracion equivalente, o {@link Optional#empty()}
     *     si el evento de dominio no tiene traduccion a integracion en este
     *     modulo
     */
    public static Optional<IntegrationEvent> map(Object domainEvent) {
        if (domainEvent instanceof CorrectionRequested event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "corrections.correction-requested.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    AGGREGATE_TYPE,
                    Map.of(
                            "correctionId", event.aggregateId(),
                            "workdayId", event.workdayId(),
                            "requestedBy", event.requestedBy())));
        }
        if (domainEvent instanceof CorrectionApproved event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "corrections.correction-approved.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    AGGREGATE_TYPE,
                    Map.of(
                            "correctionId", event.aggregateId(),
                            "workdayId", event.workdayId(),
                            "resolvedBy", event.resolvedBy())));
        }
        if (domainEvent instanceof CorrectionRejected event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "corrections.correction-rejected.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    AGGREGATE_TYPE,
                    Map.of(
                            "correctionId", event.aggregateId(),
                            "workdayId", event.workdayId(),
                            "resolvedBy", event.resolvedBy())));
        }
        return Optional.empty();
    }
}
