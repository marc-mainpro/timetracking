package com.tfp.timetracking.tenant.domain;

/**
 * Estados del ciclo de vida de un {@link Tenant} (V2, RF-TEN-004, diseño §7.1).
 *
 * <ul>
 *   <li>{@code PENDING}: solicitud creada, aún no operativa (registro público
 *       controlado, pendiente de activación por {@code PLATFORM_ADMIN}).
 *   <li>{@code ACTIVE}: puede operar con normalidad.
 *   <li>{@code SUSPENDED}: bloqueado temporalmente; sus usuarios no pueden
 *       operar hasta la reactivación.
 *   <li>{@code ARCHIVED}: retirado de forma permanente; no vuelve a operar.
 * </ul>
 *
 * <p>Solo {@code ACTIVE} permite operar (ver {@link Tenant#isActive()}).
 * Transiciones válidas en {@code Tenant} (diseño §7.2):
 * {@code PENDING → ACTIVE}, {@code ACTIVE → SUSPENDED},
 * {@code SUSPENDED → ACTIVE}, {@code ACTIVE → ARCHIVED},
 * {@code SUSPENDED → ARCHIVED}. {@code ARCHIVED} es terminal.
 */
public enum TenantStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    ARCHIVED
}
