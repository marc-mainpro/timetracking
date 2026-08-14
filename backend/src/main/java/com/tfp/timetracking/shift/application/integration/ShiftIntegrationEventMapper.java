package com.tfp.timetracking.shift.application.integration;

import com.tfp.timetracking.shared.application.IntegrationEventMapper;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.shift.domain.event.ShiftAssigned;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Traduce eventos de dominio del modulo {@code shift} a eventos de integracion
 * versionados (T170-05, CONTEXT-DOMINIO §4).
 *
 * <p>Es el primer contrato que publica este modulo. La reasignacion y el
 * archivado de plantillas siguen sin traduccion: no hay todavia ningun
 * consumidor que necesite enterarse, y publicarlas seria contrato sin
 * consumidor.
 */
@Component
public class ShiftIntegrationEventMapper implements IntegrationEventMapper {

    private static final String AGGREGATE_TYPE = "ShiftAssignment";

    @Override
    public Optional<IntegrationEvent> map(Object domainEvent) {
        if (domainEvent instanceof ShiftAssigned event) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("shiftAssignmentId", event.aggregateId());
            payload.put("employeeId", event.employeeId());
            payload.put("shiftTemplateId", event.shiftTemplateId());
            if (event.shiftTemplateName() != null) {
                payload.put("shiftTemplateName", event.shiftTemplateName());
            }
            payload.put("validFrom", event.validFrom().toString());
            // La vigencia indefinida se representa con la ausencia del campo,
            // no con una fecha centinela.
            if (event.validTo() != null) {
                payload.put("validTo", event.validTo().toString());
            }
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "shift.shift-assigned.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    AGGREGATE_TYPE,
                    payload));
        }
        return Optional.empty();
    }
}
