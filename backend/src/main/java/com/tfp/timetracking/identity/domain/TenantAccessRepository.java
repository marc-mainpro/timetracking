package com.tfp.timetracking.identity.domain;

import java.util.UUID;

/**
 * Puerto de identidad para consultar si un tenant puede operar sin crear una
 * dependencia Java desde identity hacia el modulo tenant.
 */
public interface TenantAccessRepository {

    boolean isActive(UUID tenantId);
}
