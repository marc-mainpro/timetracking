package com.tfp.timetracking.absence.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class InactiveAbsenceTypeException extends DomainException {

    public InactiveAbsenceTypeException() {
        super("ABSENCE_TYPE_INACTIVE", "El tipo de ausencia está inactivo");
    }
}
