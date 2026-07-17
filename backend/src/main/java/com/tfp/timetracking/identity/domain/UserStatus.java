package com.tfp.timetracking.identity.domain;

/**
 * Estados posibles de un {@link User} (CONTEXT-DOMINIO §1). Un usuario
 * {@code INACTIVE} no se autentica.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE
}
