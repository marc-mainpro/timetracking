package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class InvalidPasswordResetTokenException extends DomainException {

    public InvalidPasswordResetTokenException() {
        super("INVALID_PASSWORD_RESET_TOKEN", "El token de recuperacion no es valido");
    }
}
