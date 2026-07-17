package com.tfp.timetracking.timetracking.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class BreakAlreadyOpenException extends DomainException {

    public BreakAlreadyOpenException() {
        super("BREAK_ALREADY_OPEN", "Ya existe una pausa abierta para esta jornada");
    }
}
