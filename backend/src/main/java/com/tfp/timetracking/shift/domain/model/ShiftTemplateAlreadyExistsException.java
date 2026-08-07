package com.tfp.timetracking.shift.domain.model;

import com.tfp.timetracking.shared.domain.DomainException;

public final class ShiftTemplateAlreadyExistsException extends DomainException {

    public ShiftTemplateAlreadyExistsException() {
        super("SHIFT_TEMPLATE_ALREADY_EXISTS", "Ya existe una plantilla de turno con ese nombre");
    }
}
