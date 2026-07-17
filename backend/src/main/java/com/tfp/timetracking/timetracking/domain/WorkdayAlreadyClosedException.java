package com.tfp.timetracking.timetracking.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class WorkdayAlreadyClosedException extends DomainException {

    public WorkdayAlreadyClosedException() {
        super("WORKDAY_ALREADY_CLOSED", "La jornada ya esta cerrada o ajustada");
    }
}
