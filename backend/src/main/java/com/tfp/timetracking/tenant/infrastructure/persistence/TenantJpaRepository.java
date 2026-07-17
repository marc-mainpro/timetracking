package com.tfp.timetracking.tenant.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data para {@link TenantJpaEntity}. Uso interno del adaptador. */
interface TenantJpaRepository extends JpaRepository<TenantJpaEntity, UUID> {}
