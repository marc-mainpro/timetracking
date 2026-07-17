package com.tfp.timetracking.corrections.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class CorrectionAlreadyPendingException extends DomainException {

    public CorrectionAlreadyPendingException() {
        super("CORRECTION_ALREADY_PENDING", "Ya existe una solicitud pendiente para esta jornada y usuario");
    }
}
