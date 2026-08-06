package com.tfp.timetracking.absence.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AbsenceRequestJpaRepository extends JpaRepository<AbsenceRequestJpaEntity, UUID> {

    Optional<AbsenceRequestJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("""
            select request from AbsenceRequestJpaEntity request
            where request.tenantId = :tenantId
              and request.employeeId = :employeeId
              and request.startDate <= :to
              and request.endDate >= :from
            order by request.startDate asc, request.endDate asc
            """)
    List<AbsenceRequestJpaEntity> findByEmployeeAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select request from AbsenceRequestJpaEntity request
            where request.tenantId = :tenantId
              and request.employeeId = :employeeId
              and request.status = 'APPROVED'
              and request.startDate <= :to
              and request.endDate >= :from
            order by request.startDate asc, request.endDate asc
            """)
    List<AbsenceRequestJpaEntity> findApprovedByEmployeeAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select request from AbsenceRequestJpaEntity request
            where request.tenantId = :tenantId
              and request.startDate <= :to
              and request.endDate >= :from
            order by request.employeeId asc, request.startDate asc, request.endDate asc
            """)
    List<AbsenceRequestJpaEntity> findByTenantAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
