package com.tfp.timetracking.timetracking.application.integration;

import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.timetracking.domain.event.WorkdayClosed;
import com.tfp.timetracking.timetracking.domain.event.WorkdayStarted;
import java.util.Map;
import java.util.Optional;

/**
 * Traduce eventos de dominio del modulo {@code timetracking} a eventos de
 * integracion versionados (T702, CONTEXT-DOMINIO §4). Solo depende del
 * tipo {@link IntegrationEvent} de {@code shared.domain}, nunca de
 * infraestructura de outbox (ver {@code OutboxEncapsulationTest}).
 *
 * <p><strong>Decision de alcance (MVP, ficha T702):</strong> {@code
 * BreakStarted}/{@code BreakEnded} NO se traducen a evento de integracion.
 * Son eventos de grano fino de interes solo interno (auditoria/consistencia
 * del agregado {@code Workday}); ningun consumidor externo previsto en el
 * catalogo ({@code docs/integration/event-catalog.md}) los necesita, y
 * publicarlos ampliaria innecesariamente la superficie de contrato externo.
 * Si en el futuro un consumidor los necesita, se anadiran aqui como
 * {@code time-tracking.break-started.v1}/{@code .break-ended.v1} sin romper
 * compatibilidad con los tipos ya publicados.
 */
public final class TimeTrackingIntegrationEventMapper {

    private static final String AGGREGATE_TYPE = "Workday";

    private TimeTrackingIntegrationEventMapper() {}

    /**
     * @param domainEvent evento de dominio recogido tras persistir el agregado
     * @return el evento de integracion equivalente, o {@link Optional#empty()}
     *     si el evento de dominio no tiene traduccion a integracion en este
     *     modulo (incluye deliberadamente {@code BreakStarted}/{@code
     *     BreakEnded}, ver javadoc de la clase)
     */
    public static Optional<IntegrationEvent> map(Object domainEvent) {
        if (domainEvent instanceof WorkdayStarted event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "time-tracking.workday-started.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    AGGREGATE_TYPE,
                    Map.of(
                            "workdayId", event.aggregateId(),
                            "employeeId", event.employeeId(),
                            "startedAt", event.startedAt())));
        }
        if (domainEvent instanceof WorkdayClosed event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "time-tracking.workday-closed.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    AGGREGATE_TYPE,
                    Map.of(
                            "workdayId", event.aggregateId(),
                            "employeeId", event.employeeId(),
                            "startedAt", event.startedAt(),
                            "endedAt", event.endedAt())));
        }
        // BreakStarted/BreakEnded: sin traduccion a integracion en el MVP (ver javadoc).
        return Optional.empty();
    }
}
