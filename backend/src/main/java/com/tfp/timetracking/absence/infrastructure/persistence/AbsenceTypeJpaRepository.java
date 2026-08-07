package com.tfp.timetracking.absence.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AbsenceTypeJpaRepository extends JpaRepository<AbsenceTypeJpaEntity, UUID> {

    Optional<AbsenceTypeJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<AbsenceTypeJpaEntity> findByTenantIdAndCode(UUID tenantId, String code);

    List<AbsenceTypeJpaEntity> findByTenantIdOrderByCodeAsc(UUID tenantId);
}
