package com.tfp.timetracking.shift.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "shift_template")
public class ShiftTemplateJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "planned_break_minutes", nullable = false)
    private int plannedBreakMinutes;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    protected ShiftTemplateJpaEntity() {}

    public ShiftTemplateJpaEntity(
            UUID id,
            UUID tenantId,
            String name,
            LocalTime startTime,
            LocalTime endTime,
            int plannedBreakMinutes,
            String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.plannedBreakMinutes = plannedBreakMinutes;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getPlannedBreakMinutes() { return plannedBreakMinutes; }
    public String getStatus() { return status; }
}
