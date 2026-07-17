package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super("INVALID_REFRESH_TOKEN", "Refresh token invalido o expirado");
    }
}
