package com.tfp.timetracking.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data para {@link UserJpaEntity}. Uso interno del adaptador. */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    List<UserJpaEntity> findAllByTenantId(UUID tenantId);
}
