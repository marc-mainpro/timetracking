package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Credenciales invalidas");
    }
}
