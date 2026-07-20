package com.tfp.timetracking.identity.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.identity.domain.event.EmployeeCreated;
import com.tfp.timetracking.identity.domain.event.EmployeeDeactivated;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityIntegrationEventMapperTest {

    @Test
    void mapsEmployeeCreatedToIntegrationEventWithFullEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-20T09:00:00Z");
        EmployeeCreated domainEvent = new EmployeeCreated(
                eventId, occurredAt, tenantId, employeeId, "jane.doe@acme.test", Set.of("EMPLOYEE"));

        Optional<IntegrationEvent> mapped = IdentityIntegrationEventMapper.map(domainEvent);

        assertThat(mapped).isPresent();
        IntegrationEvent event = mapped.orElseThrow();
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.eventType()).isEqualTo("identity.employee-created.v1");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.aggregateId()).isEqualTo(employeeId);
        assertThat(event.aggregateType()).isEqualTo("Employee");
        assertThat(event.payload()).containsEntry("employeeId", employeeId);
        assertThat(event.payload()).containsEntry("email", "jane.doe@acme.test");
        @SuppressWarnings("unchecked")
        Iterable<String> roles = (Iterable<String>) event.payload().get("roles");
        assertThat(roles).containsExactly("EMPLOYEE");
    }

    @Test
    void mapsEmployeeDeactivatedToIntegrationEventWithMinimalPayload() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-20T09:00:00Z");
        EmployeeDeactivated domainEvent = new EmployeeDeactivated(eventId, occurredAt, tenantId, employeeId);

        Optional<IntegrationEvent> mapped = IdentityIntegrationEventMapper.map(domainEvent);

        assertThat(mapped).isPresent();
        IntegrationEvent event = mapped.orElseThrow();
        assertThat(event.eventType()).isEqualTo("identity.employee-deactivated.v1");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.aggregateType()).isEqualTo("Employee");
        assertThat(event.payload()).containsExactlyEntriesOf(java.util.Map.of("employeeId", employeeId));
    }

    @Test
    void ignoresUnknownDomainEvents() {
        Optional<IntegrationEvent> mapped = IdentityIntegrationEventMapper.map(new Object());

        assertThat(mapped).isEmpty();
    }
}
