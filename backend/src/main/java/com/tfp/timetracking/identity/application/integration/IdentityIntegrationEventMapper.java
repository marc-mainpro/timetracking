package com.tfp.timetracking.identity.application.integration;

import com.tfp.timetracking.identity.domain.event.EmployeeCreated;
import com.tfp.timetracking.identity.domain.event.EmployeeDeactivated;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Traduce eventos de dominio del modulo {@code identity} a eventos de
 * integracion versionados (T702, CONTEXT-DOMINIO §4). Solo depende del
 * tipo {@link IntegrationEvent} de {@code shared.domain}, nunca de
 * infraestructura de outbox (ver {@code OutboxEncapsulationTest}).
 *
 * <p>El agregado de dominio es {@code identity.domain.User}, pero el
 * catalogo de eventos de integracion usa el termino de negocio "empleado"
 * ({@code identity.employee-created.v1}), por eso {@code aggregateType} aqui
 * es {@code "Employee"} y no {@code "User"}.
 */
public final class IdentityIntegrationEventMapper {

    private static final String AGGREGATE_TYPE = "Employee";

    private IdentityIntegrationEventMapper() {}

    /**
     * @param domainEvent evento de dominio recogido tras persistir el agregado
     * @return el evento de integracion equivalente, o {@link Optional#empty()}
     *     si el evento de dominio no tiene traduccion a integracion en este
     *     modulo
     */
    public static Optional<IntegrationEvent> map(Object domainEvent) {
        if (domainEvent instanceof EmployeeCreated event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "identity.employee-created.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    AGGREGATE_TYPE,
                    Map.of(
                            "employeeId", event.aggregateId(),
                            "email", event.email(),
                            "roles", List.copyOf(event.roles()))));
        }
        if (domainEvent instanceof EmployeeDeactivated event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "identity.employee-deactivated.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    AGGREGATE_TYPE,
                    Map.of("employeeId", event.aggregateId())));
        }
        return Optional.empty();
    }
}
