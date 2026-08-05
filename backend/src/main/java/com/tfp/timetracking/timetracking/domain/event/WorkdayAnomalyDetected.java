package com.tfp.timetracking.timetracking.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkdayAnomalyDetected(
        UUID eventId,
        Instant occurredAt,
        UUID tenantId,
        UUID aggregateId,
        UUID employeeId,
        List<String> anomalies,
        long expectedMinutes,
        long workedMinutes,
        long pausedMinutes,
        long overtimeMinutes) {

    public WorkdayAnomalyDetected {
        anomalies = List.copyOf(anomalies);
    }
}
