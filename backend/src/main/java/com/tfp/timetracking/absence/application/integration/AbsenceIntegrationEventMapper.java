package com.tfp.timetracking.absence.application.integration;

import com.tfp.timetracking.absence.domain.event.AbsenceApproved;
import com.tfp.timetracking.absence.domain.event.AbsenceRejected;
import com.tfp.timetracking.absence.domain.event.AbsenceRequested;
import com.tfp.timetracking.shared.application.IntegrationEventMapper;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Traduce a eventos de integracion las resoluciones de ausencia (T110-04,
 * RF-NOT-003).
 *
 * <p>Se traducen la solicitud, la aprobacion y el rechazo. La cancelacion no:
 * la hace el propio empleado, que ya sabe que ha ocurrido, asi que publicarla
 * seguiria siendo contrato sin consumidor.
 *
 * <p><b>La solicitud no se publicaba</b> por ese mismo motivo: tampoco tenia
 * consumidor, porque el unico destinatario posible era el propio solicitante.
 * T170-06 crea ese consumidor —el aviso al administrador que debe resolverla—,
 * asi que la premisa deja de sostenerse y el evento pasa a estar justificado.
 * El cambio es aditivo: no altera el contrato de los eventos ya publicados.
 */
@Component
public class AbsenceIntegrationEventMapper implements IntegrationEventMapper {

    private static final String AGGREGATE_TYPE = "AbsenceRequest";

    @Override
    public Optional<IntegrationEvent> map(Object domainEvent) {
        if (domainEvent instanceof AbsenceRequested event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "absence.absence-requested.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    AGGREGATE_TYPE,
                    Map.of(
                            "absenceRequestId", event.aggregateId(),
                            "employeeId", event.employeeId(),
                            "absenceTypeId", event.absenceTypeId(),
                            "startDate", event.startDate().toString(),
                            "endDate", event.endDate().toString())));
        }
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
