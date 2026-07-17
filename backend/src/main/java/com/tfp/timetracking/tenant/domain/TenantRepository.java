package com.tfp.timetracking.tenant.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de dominio para persistir y recuperar {@link Tenant} (CONTEXT-GLOBAL
 * §4: puertos definidos en dominio, implementados en infraestructura).
 */
public interface TenantRepository {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(UUID id);

    boolean existsById(UUID id);
}
