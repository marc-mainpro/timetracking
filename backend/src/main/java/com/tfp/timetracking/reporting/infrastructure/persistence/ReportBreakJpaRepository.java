package com.tfp.timetracking.reporting.infrastructure.persistence;

import com.tfp.timetracking.timetracking.infrastructure.persistence.BreakEntryJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consulta de proyeccion de pausas cerradas para informes (T801). Solo se
 * consultan una vez conocidos los ids de jornada relevantes (evita el join
 * fetch de {@code WorkdayJpaEntity.breaks} completo, pensado para el listado
 * interactivo de jornadas, no para agregacion masiva).
 */
interface ReportBreakJpaRepository extends JpaRepository<BreakEntryJpaEntity, UUID> {

    @Query("""
            select new com.tfp.timetracking.reporting.infrastructure.persistence.ReportBreakRow(
                b.workday.id, b.startedAt, b.endedAt)
            from BreakEntryJpaEntity b
            where b.workday.id in :workdayIds
              and b.endedAt is not null
            """)
    List<ReportBreakRow> findClosedBreaksByWorkdayIds(@Param("workdayIds") Collection<UUID> workdayIds);
}
