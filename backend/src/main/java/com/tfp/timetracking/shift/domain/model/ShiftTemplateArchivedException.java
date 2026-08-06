package com.tfp.timetracking.shift.domain.model;

import com.tfp.timetracking.shared.domain.DomainException;

public final class ShiftTemplateArchivedException extends DomainException {

    public ShiftTemplateArchivedException() {
        super("SHIFT_TEMPLATE_ARCHIVED", "La plantilla de turno está archivada");
    }
}
