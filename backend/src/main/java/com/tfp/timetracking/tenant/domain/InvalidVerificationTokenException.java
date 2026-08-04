package com.tfp.timetracking.tenant.domain;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * Token de verificación inválido, caducado o ya usado (T53-05).
 *
 * <p>Deliberadamente <b>un único errorCode para los tres casos</b>: distinguir
 * «no existe» de «ya usado» convertiría el endpoint de verificación en un
 * oráculo sobre qué tokens han existido alguna vez.
 */
public final class InvalidVerificationTokenException extends DomainException {

    public InvalidVerificationTokenException() {
        super("INVALID_VERIFICATION_TOKEN", "El enlace de verificación no es válido o ha caducado");
    }
}
