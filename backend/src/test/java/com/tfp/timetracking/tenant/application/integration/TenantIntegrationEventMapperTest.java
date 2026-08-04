package com.tfp.timetracking.tenant.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.tenant.domain.event.TenantActivated;
import com.tfp.timetracking.tenant.domain.event.TenantArchived;
import com.tfp.timetracking.tenant.domain.event.TenantReactivated;
import com.tfp.timetracking.tenant.domain.event.TenantRegistered;
import com.tfp.timetracking.tenant.domain.event.TenantSuspended;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantIntegrationEventMapperTest {

    private static final TenantIntegrationEventMapper MAPPER = new TenantIntegrationEventMapper();

    @Test
    void mapsTenantRegisteredToIntegrationEventWithFullEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-20T09:00:00Z");
        TenantRegistered domainEvent = new TenantRegistered(eventId, occurredAt, tenantId, tenantId, "Acme Corp", "Europe/Madrid");

        Optional<IntegrationEvent> mapped = MAPPER.map(domainEvent);

        assertThat(mapped).isPresent();
        IntegrationEvent event = mapped.orElseThrow();
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.eventType()).isEqualTo("tenant.registered.v1");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.aggregateId()).isEqualTo(tenantId);
        assertThat(event.aggregateType()).isEqualTo("Tenant");
        assertThat(event.payload())
                .containsExactlyInAnyOrderEntriesOf(
                        java.util.Map.of("tenantId", tenantId, "name", "Acme Corp", "timezone", "Europe/Madrid"));
    }

    @Test
    void mapsTenantActivatedWithoutReason() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-21T09:00:00Z");

        IntegrationEvent event = MAPPER.map(
                        new TenantActivated(eventId, occurredAt, tenantId, tenantId))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("tenant.activated.v1");
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.payload()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("tenantId", tenantId));
    }

    @Test
    void mapsTenantSuspendedWithReason() {
        UUID tenantId = UUID.randomUUID();
        IntegrationEvent event = MAPPER.map(new TenantSuspended(
                        UUID.randomUUID(), Instant.parse("2026-07-21T09:00:00Z"), tenantId, tenantId, "Impago"))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("tenant.suspended.v1");
        assertThat(event.payload())
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of("tenantId", tenantId, "reason", "Impago"));
    }

    @Test
    void mapsTenantReactivatedAndArchived() {
        UUID tenantId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-21T09:00:00Z");

        assertThat(MAPPER.map(new TenantReactivated(UUID.randomUUID(), occurredAt, tenantId, tenantId))
                        .orElseThrow()
                        .eventType())
                .isEqualTo("tenant.reactivated.v1");
        assertThat(MAPPER.map(
                                new TenantArchived(UUID.randomUUID(), occurredAt, tenantId, tenantId, null))
                        .orElseThrow()
                        .eventType())
                .isEqualTo("tenant.archived.v1");
    }

    @Test
    void ignoresUnknownDomainEvents() {
        Optional<IntegrationEvent> mapped = MAPPER.map(new Object());

        assertThat(mapped).isEmpty();
    }
}
