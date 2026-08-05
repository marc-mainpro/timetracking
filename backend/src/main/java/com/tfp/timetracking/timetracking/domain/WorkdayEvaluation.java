package com.tfp.timetracking.timetracking.domain;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.timetracking.domain.event.WorkdayAnomalyDetected;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class WorkdayEvaluation {

    private final UUID workdayId;
    private final UUID tenantId;
    private final UUID employeeId;
    private final Duration expectedDuration;
    private final Duration workedDuration;
    private final Duration pausedDuration;
    private final Duration overtimeDuration;
    private final List<WorkdayAnomaly> anomalies;
    private final Instant evaluatedAt;
    private final List<Object> domainEvents = new ArrayList<>();

    private WorkdayEvaluation(
            UUID workdayId,
            UUID tenantId,
            UUID employeeId,
            Duration expectedDuration,
            Duration workedDuration,
            Duration pausedDuration,
            Duration overtimeDuration,
            List<WorkdayAnomaly> anomalies,
            Instant evaluatedAt) {
        this.workdayId = workdayId;
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.expectedDuration = expectedDuration;
        this.workedDuration = workedDuration;
        this.pausedDuration = pausedDuration;
        this.overtimeDuration = overtimeDuration;
        this.anomalies = anomalies;
        this.evaluatedAt = evaluatedAt;
    }

    public static WorkdayEvaluation create(
            UUID workdayId,
            UUID tenantId,
            UUID employeeId,
            Duration expectedDuration,
            Duration workedDuration,
            Duration pausedDuration,
            Duration overtimeDuration,
            List<WorkdayAnomaly> anomalies,
            Clock clock,
            IdGenerator idGenerator) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        WorkdayEvaluation evaluation = new WorkdayEvaluation(
                Objects.requireNonNull(workdayId, "workdayId no puede ser null"),
                Objects.requireNonNull(tenantId, "tenantId no puede ser null"),
                Objects.requireNonNull(employeeId, "employeeId no puede ser null"),
                nonNullDuration(expectedDuration),
                nonNullDuration(workedDuration),
                nonNullDuration(pausedDuration),
                nonNullDuration(overtimeDuration),
                List.copyOf(Objects.requireNonNull(anomalies, "anomalies no puede ser null")),
                clock.now());
        if (!evaluation.anomalies.isEmpty()) {
            evaluation.domainEvents.add(new WorkdayAnomalyDetected(
                    idGenerator.newId(),
                    evaluation.evaluatedAt,
                    tenantId,
                    workdayId,
                    employeeId,
                    evaluation.anomalies.stream().map(Enum::name).toList(),
                    evaluation.expectedDuration.toMinutes(),
                    evaluation.workedDuration.toMinutes(),
                    evaluation.pausedDuration.toMinutes(),
                    evaluation.overtimeDuration.toMinutes()));
        }
        return evaluation;
    }

    public static WorkdayEvaluation reconstitute(
            UUID workdayId,
            UUID tenantId,
            UUID employeeId,
            Duration expectedDuration,
            Duration workedDuration,
            Duration pausedDuration,
            Duration overtimeDuration,
            List<WorkdayAnomaly> anomalies,
            Instant evaluatedAt) {
        return new WorkdayEvaluation(
                Objects.requireNonNull(workdayId, "workdayId no puede ser null"),
                Objects.requireNonNull(tenantId, "tenantId no puede ser null"),
                Objects.requireNonNull(employeeId, "employeeId no puede ser null"),
                nonNullDuration(expectedDuration),
                nonNullDuration(workedDuration),
                nonNullDuration(pausedDuration),
                nonNullDuration(overtimeDuration),
                List.copyOf(Objects.requireNonNull(anomalies, "anomalies no puede ser null")),
                Objects.requireNonNull(evaluatedAt, "evaluatedAt no puede ser null"));
    }

    private static Duration nonNullDuration(Duration duration) {
        return duration == null ? Duration.ZERO : duration;
    }

    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    public UUID workdayId() { return workdayId; }
    public UUID tenantId() { return tenantId; }
    public UUID employeeId() { return employeeId; }
    public Duration expectedDuration() { return expectedDuration; }
    public Duration workedDuration() { return workedDuration; }
    public Duration pausedDuration() { return pausedDuration; }
    public Duration overtimeDuration() { return overtimeDuration; }
    public List<WorkdayAnomaly> anomalies() { return anomalies; }
    public Instant evaluatedAt() { return evaluatedAt; }
}
