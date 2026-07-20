package com.tfp.timetracking.reporting.infrastructure.persistence;

import com.tfp.timetracking.timetracking.infrastructure.persistence.WorkdayJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consulta de proyeccion dedicada a informes (T801): solo columnas
 * necesarias para el calculo, sin cargar el agregado {@code Workday}
 * completo (ver Javadoc de {@code WorkdaySummaryQueryPort}).
 */
interface ReportWorkdayJpaRepository extends JpaRepository<WorkdayJpaEntity, UUID> {

    @Query("""
            select new com.tfp.timetracking.reporting.infrastructure.persistence.ReportWorkdayRow(
                w.id, w.employeeId, w.status, w.startedAt, w.endedAt)
            from WorkdayJpaEntity w
            where w.tenantId = :tenantId
              and w.employeeId = :employeeId
              and w.startedAt >= :from
              and w.startedAt <= :to
            """)
    List<ReportWorkdayRow> findRowsByEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select new com.tfp.timetracking.reporting.infrastructure.persistence.ReportWorkdayRow(
                w.id, w.employeeId, w.status, w.startedAt, w.endedAt)
            from WorkdayJpaEntity w
            where w.tenantId = :tenantId
              and w.startedAt >= :from
              and w.startedAt <= :to
            """)
    List<ReportWorkdayRow> findRowsByTenant(@Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to);
}
