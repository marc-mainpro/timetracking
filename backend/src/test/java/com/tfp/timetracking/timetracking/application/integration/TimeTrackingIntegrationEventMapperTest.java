package com.tfp.timetracking.timetracking.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.timetracking.domain.event.WorkdayAnomalyDetected;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TimeTrackingIntegrationEventMapperTest {

    private static final TimeTrackingIntegrationEventMapper MAPPER = new TimeTrackingIntegrationEventMapper();

    @Test
    void mapsWorkdayAnomalyDetected() {
        IntegrationEvent event = MAPPER.map(new WorkdayAnomalyDetected(
                        UUID.randomUUID(),
                        Instant.parse("2026-01-15T18:00:00Z"),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        List.of("MAX_DAILY_WORK_EXCEEDED"),
                        480,
                        540,
                        15,
                        60))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("time-tracking.workday-anomaly-detected.v1");
        assertThat(event.payload()).containsEntry("expectedMinutes", 480L);
        assertThat(event.payload()).containsEntry("workedMinutes", 540L);
        assertThat(event.payload()).containsEntry("overtimeMinutes", 60L);
    }
}
