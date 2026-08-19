package com.tfp.timetracking.tenant.domain;

import com.tfp.timetracking.shared.domain.PagedResult;
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

    /**
     * Listado paginado de tenants para la administración de plataforma
     * (RF-TEN-001), excluyendo el tenant indicado (el de sistema). Si
     * {@code status} no es {@code null}, filtra por ese estado. Si
     * {@code name} no es {@code null}, busca por coincidencia parcial sin
     * distinguir mayúsculas/minúsculas. Ordenado por fecha de creación
     * descendente.
     */
    PagedResult<Tenant> findAllExcluding(UUID excludedId, TenantStatus status, String name, int page, int size);
}
