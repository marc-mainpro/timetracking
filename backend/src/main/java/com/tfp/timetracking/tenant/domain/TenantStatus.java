package com.tfp.timetracking.tenant.domain;

/**
 * Estados posibles de un {@link Tenant} (CONTEXT-DOMINIO §1).
 *
 * <p>Un tenant {@code INACTIVE} no puede operar: ninguna operacion de
 * negocio se permite sobre el ni sobre sus usuarios.
 */
public enum TenantStatus {
    ACTIVE,
    INACTIVE
}
