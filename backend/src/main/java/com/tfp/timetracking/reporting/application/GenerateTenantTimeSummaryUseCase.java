package com.tfp.timetracking.reporting.application;

import com.tfp.timetracking.reporting.domain.TenantEmployeeSummary;
import com.tfp.timetracking.reporting.domain.ReportDateRange;
import com.tfp.timetracking.reporting.domain.TimeSummaryCalculator;
import com.tfp.timetracking.reporting.domain.WorkdaySummaryQueryPort;
import com.tfp.timetracking.shared.application.TenantContext;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Informe agregado por empleado de todo el tenant en un rango de fechas
 * (T801, {@code GET /api/v1/reports/tenant/summary}). Solo {@code TENANT_ADMIN}
 * (aplicado en el controller con {@code @PreAuthorize}).
 *
 * <p>Tambien es la fuente de datos de la exportacion CSV
 * ({@code GET /api/v1/reports/tenant/export.csv}), que expone exactamente el
 * mismo dato en {@code text/csv} (CONTEXT-API §2): no hay un caso de uso
 * separado que duplique esta consulta, solo un formateador distinto en la
 * capa REST.
 */
@Service
public class GenerateTenantTimeSummaryUseCase {

    private final WorkdaySummaryQueryPort workdaySummaryQueryPort;
    private final TenantContext tenantContext;

    public GenerateTenantTimeSummaryUseCase(WorkdaySummaryQueryPort workdaySummaryQueryPort, TenantContext tenantContext) {
        this.workdaySummaryQueryPort = workdaySummaryQueryPort;
        this.tenantContext = tenantContext;
    }

    public List<TenantEmployeeSummary> generate(Instant from, Instant to) {
        ReportDateRange range = new ReportDateRange(from, to);
        return TimeSummaryCalculator.summarizeTotalsByEmployee(
                workdaySummaryQueryPort.findByTenant(tenantContext.currentTenantId(), range.from(), range.to()));
    }
}
