package com.tfp.timetracking.identity.domain;

/**
 * Roles del sistema (CONTEXT-GLOBAL §6, RF actores §4).
 *
 * <ul>
 *   <li>{@code PLATFORM_ADMIN}: administrador global de la plataforma; gestiona
 *       el ciclo de vida de los tenants y la auditoría de plataforma. No es
 *       asignable desde la administración de un tenant ni por registro público
 *       (RF-TEN, T50-04): solo se aprovisiona de forma controlada.
 *   <li>{@code TENANT_ADMIN}: gestiona empleados, correcciones, informes y
 *       auditoría dentro de su tenant.
 *   <li>{@code EMPLOYEE}: gestiona su propia jornada, historial y correcciones.
 * </ul>
 */
public enum Role {
    PLATFORM_ADMIN,
    TENANT_ADMIN,
    EMPLOYEE;

    /**
     * Indica si el rol puede asignarse dentro del ámbito de un tenant (por un
     * {@code TENANT_ADMIN} o en el alta de empleados). Los roles de plataforma
     * quedan excluidos: solo se aprovisionan de forma controlada.
     */
    public boolean isAssignableWithinTenant() {
        return this != PLATFORM_ADMIN;
    }

    /**
     * Traduce nombres de rol a {@link Role} para operaciones con ámbito de
     * tenant, rechazando cualquier rol de plataforma (RF-TEN, T50-04). Lanza
     * {@link PlatformRoleNotAssignableException} si algún nombre corresponde a
     * un rol no asignable dentro del tenant, e {@link IllegalArgumentException}
     * si el nombre no es un rol válido.
     */
    public static java.util.Set<Role> tenantAssignableRoles(java.util.Set<String> names) {
        java.util.Set<Role> roles =
                names.stream().map(Role::valueOf).collect(java.util.stream.Collectors.toCollection(() -> java.util.EnumSet.noneOf(Role.class)));
        if (roles.stream().anyMatch(role -> !role.isAssignableWithinTenant())) {
            throw new PlatformRoleNotAssignableException();
        }
        return roles;
    }
}
