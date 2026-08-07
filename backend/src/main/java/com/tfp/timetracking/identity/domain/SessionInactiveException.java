package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class SessionInactiveException extends DomainException {

    public SessionInactiveException() {
        super("SESSION_INACTIVE", "La sesion no esta activa");
    }
}
