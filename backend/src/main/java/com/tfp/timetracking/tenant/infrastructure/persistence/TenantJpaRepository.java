package com.tfp.timetracking.tenant.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data para {@link TenantJpaEntity}. Uso interno del adaptador. */
interface TenantJpaRepository extends JpaRepository<TenantJpaEntity, UUID> {

    Page<TenantJpaEntity> findByIdNot(UUID excludedId, Pageable pageable);

    Page<TenantJpaEntity> findByIdNotAndStatus(UUID excludedId, String status, Pageable pageable);

    Page<TenantJpaEntity> findByIdNotAndNameContainingIgnoreCase(UUID excludedId, String name, Pageable pageable);

    Page<TenantJpaEntity> findByIdNotAndStatusAndNameContainingIgnoreCase(
            UUID excludedId, String status, String name, Pageable pageable);
}
