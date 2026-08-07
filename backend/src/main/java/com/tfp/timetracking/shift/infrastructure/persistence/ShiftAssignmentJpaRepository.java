package com.tfp.timetracking.shift.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ShiftAssignmentJpaRepository extends JpaRepository<ShiftAssignmentJpaEntity, UUID> {

    Optional<ShiftAssignmentJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ShiftAssignmentJpaEntity> findByTenantIdAndEmployeeIdOrderByValidFromAsc(UUID tenantId, UUID employeeId);

    @Query("""
            select assignment from ShiftAssignmentJpaEntity assignment
            where assignment.tenantId = :tenantId
              and assignment.employeeId = :employeeId
              and assignment.validFrom <= :date
              and (assignment.validTo is null or assignment.validTo >= :date)
            order by assignment.validFrom asc
            """)
    List<ShiftAssignmentJpaEntity> findEffectiveByEmployee(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("date") LocalDate date);
}
