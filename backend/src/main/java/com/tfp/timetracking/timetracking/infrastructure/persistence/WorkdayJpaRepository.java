package com.tfp.timetracking.timetracking.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select workday
            from WorkdayJpaEntity workday
            where workday.tenantId = :tenantId
              and workday.employeeId = :employeeId
              and workday.startedAt >= :from
              and workday.startedAt <= :to
            """)
    Page<WorkdayJpaEntity> findByEmployee(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to,
            Pageable pageable);

    @Query("""
            select workday
            from WorkdayJpaEntity workday
            where workday.tenantId = :tenantId
              and workday.startedAt >= :from
              and workday.startedAt <= :to
            """)
    Page<WorkdayJpaEntity> findByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to,
            Pageable pageable);

    @Query("""
            select workday
            from WorkdayJpaEntity workday
            where workday.tenantId = :tenantId
              and workday.employeeId = :employeeId
              and workday.startedAt >= :from
              and workday.startedAt <= :to
            """)
    Page<WorkdayJpaEntity> findByTenantAndEmployee(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to,
            Pageable pageable);
}
