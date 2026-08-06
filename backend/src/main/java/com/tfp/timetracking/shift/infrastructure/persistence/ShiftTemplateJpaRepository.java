package com.tfp.timetracking.shift.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ShiftTemplateJpaRepository extends JpaRepository<ShiftTemplateJpaEntity, UUID> {

    Optional<ShiftTemplateJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ShiftTemplateJpaEntity> findByTenantIdAndName(UUID tenantId, String name);

    List<ShiftTemplateJpaEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
}
