package com.tfp.timetracking.tenant.domain;

/**
 * Se lanza cuando se intenta una transición de estado no permitida sobre un
 * {@link Tenant} (diseño §7.2). Es una violación de invariante del agregado,
 * no un error del cliente concreto; la capa de interfaces la traduce a un
 * Problem Details 409 (ver manejadores de {@code platform}).
 */
public class IllegalTenantTransitionException extends RuntimeException {

    public IllegalTenantTransitionException(TenantStatus from, String transition) {
        super("Transición de tenant no permitida: no se puede " + transition + " desde el estado " + from);
    }
}
