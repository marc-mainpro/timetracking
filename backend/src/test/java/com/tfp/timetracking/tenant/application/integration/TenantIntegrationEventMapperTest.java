package com.tfp.timetracking.tenant.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.tenant.domain.event.TenantRegistered;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantIntegrationEventMapperTest {

    @Test
    void mapsTenantRegisteredToIntegrationEventWithFullEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-20T09:00:00Z");
        TenantRegistered domainEvent = new TenantRegistered(eventId, occurredAt, tenantId, tenantId, "Acme Corp", "Europe/Madrid");

        Optional<IntegrationEvent> mapped = TenantIntegrationEventMapper.map(domainEvent);

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
    void ignoresUnknownDomainEvents() {
        Optional<IntegrationEvent> mapped = TenantIntegrationEventMapper.map(new Object());

        assertThat(mapped).isEmpty();
    }
}
