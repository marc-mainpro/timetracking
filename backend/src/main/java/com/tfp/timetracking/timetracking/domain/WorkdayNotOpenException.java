package com.tfp.timetracking.timetracking.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class WorkdayNotOpenException extends DomainException {

    public WorkdayNotOpenException() {
        super("WORKDAY_NOT_OPEN", "La jornada debe estar abierta para realizar esta operacion");
    }
}
