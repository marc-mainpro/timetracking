package com.tfp.timetracking.absence.application.integration;

import com.tfp.timetracking.absence.domain.event.AbsenceApproved;
import com.tfp.timetracking.absence.domain.event.AbsenceRejected;
import com.tfp.timetracking.shared.application.IntegrationEventMapper;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Traduce a eventos de integracion las resoluciones de ausencia (T110-04,
 * RF-NOT-003).
 *
 * <p>Solo se traducen la aprobacion y el rechazo: son los hechos que otro
 * modulo necesita conocer para avisar al empleado. La solicitud y la
 * cancelacion las hace el propio empleado, que ya sabe que han ocurrido, asi
 * que publicarlas seria contrato sin consumidor.
 */
@Component
public class AbsenceIntegrationEventMapper implements IntegrationEventMapper {

    private static final String AGGREGATE_TYPE = "AbsenceRequest";

    @Override
    public Optional<IntegrationEvent> map(Object domainEvent) {
        if (domainEvent instanceof AbsenceApproved event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "absence.absence-approved.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    AGGREGATE_TYPE,
                    Map.of(
                            "absenceRequestId", event.aggregateId(),
                            "employeeId", event.employeeId(),
                            "resolvedBy", event.resolvedBy())));
        }
        if (domainEvent instanceof AbsenceRejected event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "absence.absence-rejected.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    AGGREGATE_TYPE,
                    Map.of(
                            "absenceRequestId", event.aggregateId(),
                            "employeeId", event.employeeId(),
                            "resolvedBy", event.resolvedBy())));
        }
        return Optional.empty();
    }
}
