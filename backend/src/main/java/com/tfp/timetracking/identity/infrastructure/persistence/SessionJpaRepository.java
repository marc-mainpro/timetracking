package com.tfp.timetracking.identity.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, UUID> {

    Optional<SessionJpaEntity> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);

    List<SessionJpaEntity> findByTenantIdAndUserIdOrderByLastUsedAtDesc(UUID tenantId, UUID userId);

    List<SessionJpaEntity> findByUserId(UUID userId);
}
