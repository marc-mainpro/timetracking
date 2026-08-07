package com.tfp.timetracking.shared.domain;

import java.util.UUID;

/**
 * Tenant de sistema al que pertenecen los usuarios {@code PLATFORM_ADMIN}
 * (T50-04). No es un tenant de negocio: se excluye del listado de plataforma
 * (RF-TEN-001) y no opera con datos de control horario.
 *
 * <p>Vive en {@code shared.domain} porque tanto {@code identity} (que crea el
 * usuario de plataforma) como {@code tenant} (que excluye este id del listado)
 * necesitan la constante sin depender el uno del otro (ver
 * {@code TenantAccessRepository} y {@code ModuleCyclesTest}).
 */
public final class PlatformTenant {

    /** Identificador fijo del tenant de sistema (creado por la migración V11). */
    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private PlatformTenant() {}
}
