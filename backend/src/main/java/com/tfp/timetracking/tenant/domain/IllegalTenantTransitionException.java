package com.tfp.timetracking.tenant.domain;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * Se lanza cuando se intenta una transición de estado no permitida sobre un
 * {@link Tenant} (diseño §7.2). Es una violación de invariante del agregado; la
 * capa de interfaces la traduce a un Problem Details 409 vía el manejador
 * global (errorCode {@code ILLEGAL_TENANT_TRANSITION}).
 */
public final class IllegalTenantTransitionException extends DomainException {

    public IllegalTenantTransitionException(TenantStatus from, String transition) {
        super(
                "ILLEGAL_TENANT_TRANSITION",
                "Transición de tenant no permitida: no se puede " + transition + " desde el estado " + from);
    }
}
