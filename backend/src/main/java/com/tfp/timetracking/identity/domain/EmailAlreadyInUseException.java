package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * Se lanza cuando ya existe un {@link User} con el mismo email. El email es
 * globalmente unico para eliminar ambiguedades durante el login por
 * {@code email + password} (ADR-0008).
 */
public final class EmailAlreadyInUseException extends DomainException {

    public EmailAlreadyInUseException(String ignoredEmail) {
        super("EMAIL_ALREADY_IN_USE", "No se ha podido completar la operacion con los datos proporcionados");
    }
}
