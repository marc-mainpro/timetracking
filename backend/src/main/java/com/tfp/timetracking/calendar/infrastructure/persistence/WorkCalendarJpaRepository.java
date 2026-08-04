package com.tfp.timetracking.calendar.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data para {@link WorkCalendarJpaEntity}. Uso interno del adaptador. */
interface WorkCalendarJpaRepository extends JpaRepository<WorkCalendarJpaEntity, UUID> {

    Optional<WorkCalendarJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<WorkCalendarJpaEntity> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    boolean existsByTenantIdAndName(UUID tenantId, String name);

    Page<WorkCalendarJpaEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<WorkCalendarJpaEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
}
