package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class UserInactiveException extends DomainException {

    public UserInactiveException() {
        super("USER_INACTIVE", "El usuario esta inactivo");
    }
}
