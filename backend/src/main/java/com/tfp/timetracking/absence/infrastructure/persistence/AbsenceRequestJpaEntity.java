package com.tfp.timetracking.absence.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "absence_request")
public class AbsenceRequestJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "absence_type_id", nullable = false)
    private UUID absenceTypeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_comment", length = 500)
    private String resolutionComment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AbsenceRequestJpaEntity() {}

    public AbsenceRequestJpaEntity(
            UUID id,
            UUID tenantId,
            UUID employeeId,
            UUID absenceTypeId,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            String status,
            UUID resolvedBy,
            Instant resolvedAt,
            String resolutionComment,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.absenceTypeId = absenceTypeId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.status = status;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
        this.resolutionComment = resolutionComment;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEmployeeId() { return employeeId; }
    public UUID getAbsenceTypeId() { return absenceTypeId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public UUID getResolvedBy() { return resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolutionComment() { return resolutionComment; }
    public Instant getCreatedAt() { return createdAt; }
}
