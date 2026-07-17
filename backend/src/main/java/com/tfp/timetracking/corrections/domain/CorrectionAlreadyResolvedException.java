package com.tfp.timetracking.corrections.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class CorrectionAlreadyResolvedException extends DomainException {

    public CorrectionAlreadyResolvedException() {
        super("CORRECTION_ALREADY_RESOLVED", "La solicitud de correccion ya fue resuelta");
    }
}
