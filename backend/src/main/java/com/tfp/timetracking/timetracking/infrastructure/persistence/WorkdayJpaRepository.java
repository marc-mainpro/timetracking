package com.tfp.timetracking.timetracking.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WorkdayJpaRepository extends JpaRepository<WorkdayJpaEntity, UUID> {

    Optional<WorkdayJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("""
            select workday
            from WorkdayJpaEntity workday
            where workday.tenantId = :tenantId
              and workday.employeeId = :employeeId
              and workday.status in ('OPEN', 'ON_BREAK')
            """)
    Optional<WorkdayJpaEntity> findActiveByEmployee(@Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);
}
