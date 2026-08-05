package com.tfp.timetracking.timetracking.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workday_evaluation")
public class WorkdayEvaluationJpaEntity {

    @Id
    @Column(name = "workday_id", nullable = false)
    private UUID workdayId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "expected_minutes", nullable = false)
    private long expectedMinutes;

    @Column(name = "worked_minutes", nullable = false)
    private long workedMinutes;

    @Column(name = "effective_worked_minutes", nullable = false)
    private long effectiveWorkedMinutes;

    @Column(name = "paused_minutes", nullable = false)
    private long pausedMinutes;

    @Column(name = "overtime_minutes", nullable = false)
    private long overtimeMinutes;

    @Column(name = "deviation_minutes", nullable = false)
    private long deviationMinutes;

    @Column(name = "anomalies", nullable = false, length = 300)
    private String anomalies;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected WorkdayEvaluationJpaEntity() {}

    public WorkdayEvaluationJpaEntity(
            UUID workdayId,
            UUID tenantId,
            UUID employeeId,
            long expectedMinutes,
            long workedMinutes,
            long effectiveWorkedMinutes,
            long pausedMinutes,
            long overtimeMinutes,
            long deviationMinutes,
            String anomalies,
            Instant evaluatedAt) {
        this.workdayId = workdayId;
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.expectedMinutes = expectedMinutes;
        this.workedMinutes = workedMinutes;
        this.effectiveWorkedMinutes = effectiveWorkedMinutes;
        this.pausedMinutes = pausedMinutes;
        this.overtimeMinutes = overtimeMinutes;
        this.deviationMinutes = deviationMinutes;
        this.anomalies = anomalies;
        this.evaluatedAt = evaluatedAt;
    }

    public UUID getWorkdayId() { return workdayId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEmployeeId() { return employeeId; }
    public long getExpectedMinutes() { return expectedMinutes; }
    public long getWorkedMinutes() { return workedMinutes; }
    public long getEffectiveWorkedMinutes() { return effectiveWorkedMinutes; }
    public long getPausedMinutes() { return pausedMinutes; }
    public long getOvertimeMinutes() { return overtimeMinutes; }
    public long getDeviationMinutes() { return deviationMinutes; }
    public String getAnomalies() { return anomalies; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
}
