package com.tfp.timetracking.timetracking.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class WorkdayAlreadyOpenException extends DomainException {

    public WorkdayAlreadyOpenException() {
        super("WORKDAY_ALREADY_OPEN", "El empleado ya tiene una jornada activa");
    }
}
