package com.tfp.timetracking.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del rol y de la regla de asignación dentro del tenant (T50-04):
 * {@code PLATFORM_ADMIN} no puede asignarse en el ámbito de un tenant.
 */
class RoleTest {

    @Test
    void platformAdminIsNotAssignableWithinTenant() {
        assertThat(Role.PLATFORM_ADMIN.isAssignableWithinTenant()).isFalse();
        assertThat(Role.TENANT_ADMIN.isAssignableWithinTenant()).isTrue();
        assertThat(Role.EMPLOYEE.isAssignableWithinTenant()).isTrue();
    }

    @Test
    void tenantAssignableRolesMapsValidTenantRoles() {
        assertThat(Role.tenantAssignableRoles(Set.of("TENANT_ADMIN", "EMPLOYEE")))
                .containsExactlyInAnyOrder(Role.TENANT_ADMIN, Role.EMPLOYEE);
    }

    @Test
    void tenantAssignableRolesRejectsPlatformRole() {
        assertThatExceptionOfType(PlatformRoleNotAssignableException.class)
                .isThrownBy(() -> Role.tenantAssignableRoles(Set.of("PLATFORM_ADMIN")));
        assertThatExceptionOfType(PlatformRoleNotAssignableException.class)
                .isThrownBy(() -> Role.tenantAssignableRoles(Set.of("EMPLOYEE", "PLATFORM_ADMIN")));
    }

    @Test
    void tenantAssignableRolesRejectsUnknownRole() {
        assertThatIllegalArgumentException().isThrownBy(() -> Role.tenantAssignableRoles(Set.of("SUPERUSER")));
    }
}
