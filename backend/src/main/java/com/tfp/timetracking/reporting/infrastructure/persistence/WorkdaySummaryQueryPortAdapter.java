package com.tfp.timetracking.reporting.infrastructure.persistence;

import com.tfp.timetracking.reporting.domain.BreakInterval;
import com.tfp.timetracking.reporting.domain.WorkdayReportEntry;
import com.tfp.timetracking.reporting.domain.WorkdaySummaryQueryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * Adaptador del puerto de agregacion de informes (T801). Ejecuta dos
 * consultas de proyeccion (jornadas, luego pausas cerradas de esas jornadas)
 * en vez de reconstruir el agregado {@code Workday} completo via
 * {@code WorkdayRepository} (ver Javadoc de {@link WorkdaySummaryQueryPort}
 * para la justificacion).
 */
@Repository
class WorkdaySummaryQueryPortAdapter implements WorkdaySummaryQueryPort {

    private static final String OPEN_STATUS_OPEN = "OPEN";
    private static final String OPEN_STATUS_ON_BREAK = "ON_BREAK";
    private static final String ADJUSTED_STATUS = "ADJUSTED";

    private final ReportWorkdayJpaRepository workdayJpaRepository;
    private final ReportBreakJpaRepository breakJpaRepository;

    WorkdaySummaryQueryPortAdapter(ReportWorkdayJpaRepository workdayJpaRepository, ReportBreakJpaRepository breakJpaRepository) {
        this.workdayJpaRepository = workdayJpaRepository;
        this.breakJpaRepository = breakJpaRepository;
    }

    @Override
    public List<WorkdayReportEntry> findByEmployee(UUID tenantId, UUID employeeId, Instant from, Instant to) {
        return toEntries(tenantId, workdayJpaRepository.findRowsByEmployee(tenantId, employeeId, from, to));
    }

    @Override
    public List<WorkdayReportEntry> findByTenant(UUID tenantId, Instant from, Instant to) {
        return toEntries(tenantId, workdayJpaRepository.findRowsByTenant(tenantId, from, to));
    }

    private List<WorkdayReportEntry> toEntries(UUID tenantId, List<ReportWorkdayRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> workdayIds = rows.stream().map(ReportWorkdayRow::id).toList();
        Map<UUID, List<BreakInterval>> breaksByWorkday = breakJpaRepository.findClosedBreaksByWorkdayIds(tenantId, workdayIds).stream()
                .collect(Collectors.groupingBy(
                        ReportBreakRow::workdayId,
                        Collectors.mapping(row -> new BreakInterval(row.startedAt(), row.endedAt()), Collectors.toList())));

        return rows.stream()
                .map(row -> new WorkdayReportEntry(
                        row.id(),
                        row.employeeId(),
                        isOpen(row.status()),
                        ADJUSTED_STATUS.equals(row.status()),
                        row.startedAt(),
                        row.endedAt(),
                        breaksByWorkday.getOrDefault(row.id(), List.of())))
                .toList();
    }

    private boolean isOpen(String status) {
        return OPEN_STATUS_OPEN.equals(status) || OPEN_STATUS_ON_BREAK.equals(status);
    }
}
