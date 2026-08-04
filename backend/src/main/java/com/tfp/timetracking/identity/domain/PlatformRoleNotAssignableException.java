package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * Se lanza cuando se intenta asignar un rol de plataforma (p.ej.
 * {@code PLATFORM_ADMIN}) dentro del ámbito de un tenant (alta de empleados o
 * cambio de roles). Los roles de plataforma solo se aprovisionan de forma
 * controlada, nunca desde la administración de un tenant ni por registro
 * público (RF-TEN, T50-04).
 */
public final class PlatformRoleNotAssignableException extends DomainException {

    public PlatformRoleNotAssignableException() {
        super("PLATFORM_ROLE_NOT_ASSIGNABLE", "Un rol de plataforma no puede asignarse dentro de un tenant");
    }
}
