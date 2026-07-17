package com.tfp.timetracking.timetracking.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class WorkdayOpenBreakException extends DomainException {

    public WorkdayOpenBreakException() {
        super("WORKDAY_OPEN_BREAK", "No se puede cerrar una jornada con una pausa abierta");
    }
}
