package com.tfp.timetracking.timetracking.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkdayEvaluationJpaRepository extends JpaRepository<WorkdayEvaluationJpaEntity, UUID> {

    Optional<WorkdayEvaluationJpaEntity> findByTenantIdAndWorkdayId(UUID tenantId, UUID workdayId);
}
