package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * Se lanza cuando ya existe un {@link User} con el mismo email dentro del
 * mismo tenant (CONTEXT-DOMINIO §1: "email unico dentro del tenant"; §2:
 * errorCode {@code EMAIL_ALREADY_IN_USE}). Dos tenants distintos SI pueden
 * compartir el mismo email.
 */
public final class EmailAlreadyInUseException extends DomainException {

    public EmailAlreadyInUseException(String email) {
        super("EMAIL_ALREADY_IN_USE", "El email " + email + " ya esta en uso en este tenant");
    }
}
