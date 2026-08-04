package com.tfp.timetracking.tenant.domain;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * Transición de estado no permitida sobre una {@link TenantRegistration}
 * (T53-01). Es una invariante del agregado, nunca una comprobación del
 * controlador; la capa REST la traduce a 409 vía el manejador global.
 */
public final class IllegalTenantRegistrationTransitionException extends DomainException {

    public IllegalTenantRegistrationTransitionException(TenantRegistrationStatus from, String transition) {
        super(
                "ILLEGAL_TENANT_REGISTRATION_TRANSITION",
                "Transición de solicitud no permitida: no se puede " + transition + " desde el estado " + from);
    }
}
