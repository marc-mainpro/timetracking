package com.tfp.timetracking.tenant.application.integration;

import com.tfp.timetracking.shared.application.IntegrationEventMapper;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.tenant.domain.event.TenantActivated;
import com.tfp.timetracking.tenant.domain.event.TenantArchived;
import com.tfp.timetracking.tenant.domain.event.TenantReactivated;
import com.tfp.timetracking.tenant.domain.event.TenantRegistered;
import com.tfp.timetracking.tenant.domain.event.TenantSuspended;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Traduce eventos de dominio del modulo {@code tenant} a eventos de
 * integracion versionados (T702, CONTEXT-DOMINIO §4). Solo depende del
 * tipo {@link IntegrationEvent} de {@code shared.domain}, nunca de
 * infraestructura de outbox (ver {@code OutboxEncapsulationTest}).
 */
@Component
public class TenantIntegrationEventMapper implements IntegrationEventMapper {

    private static final String AGGREGATE_TYPE = "Tenant";


    /**
     * @param domainEvent evento de dominio recogido tras persistir el agregado
     * @return el evento de integracion equivalente, o {@link Optional#empty()}
     *     si el evento de dominio no tiene traduccion a integracion en este
     *     modulo
     */
    @Override
    public Optional<IntegrationEvent> map(Object domainEvent) {
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
        if (domainEvent instanceof TenantActivated event) {
            return Optional.of(lifecycleEvent(
                    event.eventId(), "tenant.activated.v1", event.occurredAt(), event.aggregateId(), null));
        }
        if (domainEvent instanceof TenantSuspended event) {
            return Optional.of(lifecycleEvent(
                    event.eventId(), "tenant.suspended.v1", event.occurredAt(), event.aggregateId(), event.reason()));
        }
        if (domainEvent instanceof TenantReactivated event) {
            return Optional.of(lifecycleEvent(
                    event.eventId(), "tenant.reactivated.v1", event.occurredAt(), event.aggregateId(), null));
        }
        if (domainEvent instanceof TenantArchived event) {
            return Optional.of(lifecycleEvent(
                    event.eventId(), "tenant.archived.v1", event.occurredAt(), event.aggregateId(), event.reason()));
        }
        return Optional.empty();
    }

    private static IntegrationEvent lifecycleEvent(
            java.util.UUID eventId, String eventType, java.time.Instant occurredAt, java.util.UUID tenantId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        if (reason != null) {
            payload.put("reason", reason);
        }
        return new IntegrationEvent(eventId, eventType, 1, occurredAt, tenantId, tenantId, AGGREGATE_TYPE, payload);
    }
}
