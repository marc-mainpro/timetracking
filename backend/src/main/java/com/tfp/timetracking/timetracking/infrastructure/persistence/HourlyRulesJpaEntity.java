package com.tfp.timetracking.timetracking.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "hourly_rules")
public class HourlyRulesJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "max_daily_work_minutes")
    private Integer maxDailyWorkMinutes;

    @Column(name = "required_break_minutes")
    private Integer requiredBreakMinutes;

    @Column(name = "rounding_step_minutes")
    private Integer roundingStepMinutes;

    @Column(name = "tolerance_minutes")
    private Integer toleranceMinutes;

    protected HourlyRulesJpaEntity() {}

    public HourlyRulesJpaEntity(
            UUID tenantId, Integer maxDailyWorkMinutes, Integer requiredBreakMinutes, Integer roundingStepMinutes, Integer toleranceMinutes) {
        this.tenantId = tenantId;
        this.maxDailyWorkMinutes = maxDailyWorkMinutes;
        this.requiredBreakMinutes = requiredBreakMinutes;
        this.roundingStepMinutes = roundingStepMinutes;
        this.toleranceMinutes = toleranceMinutes;
    }

    public UUID getTenantId() { return tenantId; }
    public Integer getMaxDailyWorkMinutes() { return maxDailyWorkMinutes; }
    public Integer getRequiredBreakMinutes() { return requiredBreakMinutes; }
    public Integer getRoundingStepMinutes() { return roundingStepMinutes; }
    public Integer getToleranceMinutes() { return toleranceMinutes; }
}
