package com.tfp.timetracking.shift.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class ShiftAssignment {

    private final UUID id;
    private final UUID tenantId;
    private final UUID employeeId;
    private UUID shiftTemplateId;
    private LocalDate validFrom;
    private LocalDate validTo;

    private ShiftAssignment(
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

    public static ShiftAssignment create(
            UUID tenantId,
            UUID employeeId,
            UUID shiftTemplateId,
            LocalDate validFrom,
            LocalDate validTo,
            UUID id) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(employeeId, "employeeId no puede ser null");
        Objects.requireNonNull(shiftTemplateId, "shiftTemplateId no puede ser null");
        validatePeriod(validFrom, validTo);
        return new ShiftAssignment(id, tenantId, employeeId, shiftTemplateId, validFrom, validTo);
    }

    public static ShiftAssignment reconstitute(
            UUID id,
            UUID tenantId,
            UUID employeeId,
            UUID shiftTemplateId,
            LocalDate validFrom,
            LocalDate validTo) {
        return create(tenantId, employeeId, shiftTemplateId, validFrom, validTo, id);
    }

    public void reassign(UUID shiftTemplateId, LocalDate validFrom, LocalDate validTo) {
        this.shiftTemplateId = Objects.requireNonNull(shiftTemplateId, "shiftTemplateId no puede ser null");
        validatePeriod(validFrom, validTo);
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public boolean overlaps(ShiftAssignment other) {
        Objects.requireNonNull(other, "other no puede ser null");
        if (!tenantId.equals(other.tenantId) || !employeeId.equals(other.employeeId)) {
            return false;
        }
        LocalDate thisEnd = validTo != null ? validTo : LocalDate.MAX;
        LocalDate otherEnd = other.validTo != null ? other.validTo : LocalDate.MAX;
        return !thisEnd.isBefore(other.validFrom) && !otherEnd.isBefore(validFrom);
    }

    public boolean isEffectiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date no puede ser null");
        if (date.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || !date.isAfter(validTo);
    }

    private static void validatePeriod(LocalDate validFrom, LocalDate validTo) {
        Objects.requireNonNull(validFrom, "validFrom no puede ser null");
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("La vigencia no puede terminar antes de empezar");
        }
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID employeeId() { return employeeId; }
    public UUID shiftTemplateId() { return shiftTemplateId; }
    public LocalDate validFrom() { return validFrom; }
    public LocalDate validTo() { return validTo; }
}
