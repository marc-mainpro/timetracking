package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.DomainException;

public final class TenantInactiveException extends DomainException {

    public TenantInactiveException() {
        super("TENANT_INACTIVE", "El tenant esta inactivo");
    }
}
