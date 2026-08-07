package com.tfp.timetracking.shift.domain.model;

import com.tfp.timetracking.shared.domain.DomainException;

public final class OverlappingShiftAssignmentException extends DomainException {

    public OverlappingShiftAssignmentException() {
        super("OVERLAPPING_SHIFT_ASSIGNMENT", "El empleado ya tiene un turno asignado en un periodo solapado");
    }
}
