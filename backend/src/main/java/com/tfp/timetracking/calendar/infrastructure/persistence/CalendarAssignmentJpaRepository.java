package com.tfp.timetracking.calendar.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data para {@link CalendarAssignmentJpaEntity}. Uso interno del adaptador. */
interface CalendarAssignmentJpaRepository extends JpaRepository<CalendarAssignmentJpaEntity, UUID> {

    Optional<CalendarAssignmentJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<CalendarAssignmentJpaEntity> findByTenantIdAndScopeAndScopeTargetId(
            UUID tenantId, String scope, UUID scopeTargetId);

    Optional<CalendarAssignmentJpaEntity> findByTenantIdAndScopeAndScopeTargetIdIsNull(UUID tenantId, String scope);

    Page<CalendarAssignmentJpaEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<CalendarAssignmentJpaEntity> findByTenantIdAndCalendarId(UUID tenantId, UUID calendarId, Pageable pageable);

    /**
     * Candidatas para resolver el calendario efectivo de un empleado: la
     * asignacion de tenant, la de su equipo (si aporta uno) y la suya propia.
     * Se acota en SQL para no traerse todas las asignaciones del tenant; el
     * filtrado fino y la precedencia los aplica {@code EffectiveCalendarResolver}.
     */
    @Query("""
            SELECT a FROM CalendarAssignmentJpaEntity a
            WHERE a.tenantId = :tenantId
              AND (a.scope = 'TENANT'
                   OR (a.scope = 'EMPLOYEE' AND a.scopeTargetId = :employeeId)
                   OR (a.scope = 'TEAM' AND :teamId IS NOT NULL AND a.scopeTargetId = :teamId))
            """)
    List<CalendarAssignmentJpaEntity> findApplicable(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId, @Param("teamId") UUID teamId);
}
