package com.tfp.timetracking.timetracking.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class BreakNotOpenException extends DomainException {

    public BreakNotOpenException() {
        super("BREAK_NOT_OPEN", "No existe una pausa abierta para esta jornada");
    }
}
