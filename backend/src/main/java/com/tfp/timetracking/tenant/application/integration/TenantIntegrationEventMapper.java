package com.tfp.timetracking.tenant.application.integration;

import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.tenant.domain.event.TenantRegistered;
import java.util.Map;
import java.util.Optional;

/**
 * Traduce eventos de dominio del modulo {@code tenant} a eventos de
 * integracion versionados (T702, CONTEXT-DOMINIO §4). Solo depende del
 * tipo {@link IntegrationEvent} de {@code shared.domain}, nunca de
 * infraestructura de outbox (ver {@code OutboxEncapsulationTest}).
 */
public final class TenantIntegrationEventMapper {

    private static final String AGGREGATE_TYPE = "Tenant";

    private TenantIntegrationEventMapper() {}

    /**
     * @param domainEvent evento de dominio recogido tras persistir el agregado
     * @return el evento de integracion equivalente, o {@link Optional#empty()}
     *     si el evento de dominio no tiene traduccion a integracion en este
     *     modulo
     */
    public static Optional<IntegrationEvent> map(Object domainEvent) {
        if (domainEvent instanceof TenantRegistered event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "tenant.registered.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    AGGREGATE_TYPE,
                    Map.of(
                            "tenantId", event.aggregateId(),
                            "name", event.name(),
                            "timezone", event.timezone())));
        }
        return Optional.empty();
    }
}
