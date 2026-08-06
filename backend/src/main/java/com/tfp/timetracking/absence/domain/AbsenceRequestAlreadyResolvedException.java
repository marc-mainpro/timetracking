package com.tfp.timetracking.absence.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class AbsenceRequestAlreadyResolvedException extends DomainException {

    public AbsenceRequestAlreadyResolvedException() {
        super("ABSENCE_REQUEST_ALREADY_RESOLVED", "La solicitud de ausencia ya no está pendiente");
    }
}
