package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class LastAdminException extends DomainException {

    public LastAdminException() {
        super("LAST_ADMIN", "El tenant debe mantener al menos un TENANT_ADMIN activo");
    }
}
