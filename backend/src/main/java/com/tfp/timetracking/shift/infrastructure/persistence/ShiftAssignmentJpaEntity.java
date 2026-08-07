package com.tfp.timetracking.shift.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "shift_assignment")
public class ShiftAssignmentJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "shift_template_id", nullable = false)
    private UUID shiftTemplateId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    protected ShiftAssignmentJpaEntity() {}

    public ShiftAssignmentJpaEntity(
            UUID id,
            UUID tenantId,
            UUID employeeId,
            UUID shiftTemplateId,
            LocalDate validFrom,
            LocalDate validTo) {
        this.id = id;
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.shiftTemplateId = shiftTemplateId;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEmployeeId() { return employeeId; }
    public UUID getShiftTemplateId() { return shiftTemplateId; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
}
