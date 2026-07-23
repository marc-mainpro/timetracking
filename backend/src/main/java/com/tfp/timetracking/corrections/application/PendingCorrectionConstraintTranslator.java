package com.tfp.timetracking.corrections.application;

import com.tfp.timetracking.corrections.domain.CorrectionAlreadyPendingException;
import com.tfp.timetracking.shared.application.ConstraintViolationTranslator;
import com.tfp.timetracking.shared.domain.DomainException;
import org.springframework.stereotype.Component;

/**
 * Indice unico parcial que impide mas de una solicitud de correccion pendiente
 * por jornada y usuario.
 */
@Component
public class PendingCorrectionConstraintTranslator implements ConstraintViolationTranslator {

    @Override
    public String constraintName() {
        return "ux_correction_request_pending";
    }

    @Override
    public DomainException translate() {
        return new CorrectionAlreadyPendingException();
    }
}
