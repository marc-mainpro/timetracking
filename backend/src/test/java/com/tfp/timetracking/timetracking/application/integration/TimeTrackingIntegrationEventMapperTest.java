package com.tfp.timetracking.timetracking.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.timetracking.domain.event.BreakEnded;
import com.tfp.timetracking.timetracking.domain.event.BreakStarted;
import com.tfp.timetracking.timetracking.domain.event.WorkdayClosed;
import com.tfp.timetracking.timetracking.domain.event.WorkdayStarted;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TimeTrackingIntegrationEventMapperTest {

    @Test
    void mapsWorkdayStartedToIntegrationEventWithFullEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID workdayId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-20T09:00:00Z");
        WorkdayStarted domainEvent = new WorkdayStarted(eventId, occurredAt, tenantId, workdayId, employeeId, occurredAt);

        Optional<IntegrationEvent> mapped = TimeTrackingIntegrationEventMapper.map(domainEvent);

        assertThat(mapped).isPresent();
        IntegrationEvent event = mapped.orElseThrow();
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.eventType()).isEqualTo("time-tracking.workday-started.v1");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.aggregateId()).isEqualTo(workdayId);
        assertThat(event.aggregateType()).isEqualTo("Workday");
        assertThat(event.payload())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("workdayId", workdayId, "employeeId", employeeId, "startedAt", occurredAt));
    }

    @Test
    void mapsWorkdayClosedToIntegrationEventWithReferencePayload() {
        // Caso de referencia explicito de la ficha T702: cerrar una jornada
        // debe producir el evento time-tracking.workday-closed.v1.
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID workdayId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-07-20T09:00:00Z");
        Instant endedAt = Instant.parse("2026-07-20T17:00:00Z");
        WorkdayClosed domainEvent = new WorkdayClosed(eventId, endedAt, tenantId, workdayId, employeeId, startedAt, endedAt);

        Optional<IntegrationEvent> mapped = TimeTrackingIntegrationEventMapper.map(domainEvent);

        assertThat(mapped).isPresent();
        IntegrationEvent event = mapped.orElseThrow();
        assertThat(event.eventType()).isEqualTo("time-tracking.workday-closed.v1");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.aggregateId()).isEqualTo(workdayId);
        assertThat(event.aggregateType()).isEqualTo("Workday");
        assertThat(event.payload())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "workdayId", workdayId,
                        "employeeId", employeeId,
                        "startedAt", startedAt,
                        "endedAt", endedAt));
    }

    @Test
    void doesNotPublishBreakStartedAsIntegrationEvent() {
        BreakStarted domainEvent = new BreakStarted(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        assertThat(TimeTrackingIntegrationEventMapper.map(domainEvent)).isEmpty();
    }

    @Test
    void doesNotPublishBreakEndedAsIntegrationEvent() {
        BreakEnded domainEvent = new BreakEnded(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        assertThat(TimeTrackingIntegrationEventMapper.map(domainEvent)).isEmpty();
    }
}
