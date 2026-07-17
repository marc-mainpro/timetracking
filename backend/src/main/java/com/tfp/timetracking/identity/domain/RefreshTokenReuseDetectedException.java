package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class RefreshTokenReuseDetectedException extends DomainException {

    public RefreshTokenReuseDetectedException() {
        super("REFRESH_TOKEN_REUSED", "Se detecto reutilizacion de refresh token");
    }
}
