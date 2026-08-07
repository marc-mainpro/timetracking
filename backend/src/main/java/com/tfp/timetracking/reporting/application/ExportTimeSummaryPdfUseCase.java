package com.tfp.timetracking.reporting.application;

import com.tfp.timetracking.reporting.domain.TenantEmployeeSummary;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exporta el informe agregado del tenant en PDF (T100-06, RF-REP-006,
 * {@code GET /api/v1/reports/tenant/export.pdf}).
 *
 * <p>Reutiliza el mismo caso de uso de cálculo que el CSV y la vista web: el
 * formato es una decisión de presentación, no una consulta distinta, así que
 * duplicar la agregación abriría la puerta a que ambos informes dejaran de
 * cuadrar.
 */
@Service
public class ExportTimeSummaryPdfUseCase {

    private final GenerateTenantTimeSummaryUseCase generateTenantTimeSummaryUseCase;
    private final TimeSummaryPdfRenderer renderer;

    public ExportTimeSummaryPdfUseCase(
            GenerateTenantTimeSummaryUseCase generateTenantTimeSummaryUseCase, TimeSummaryPdfRenderer renderer) {
        this.generateTenantTimeSummaryUseCase = generateTenantTimeSummaryUseCase;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public byte[] export(Instant from, Instant to) {
        List<TenantEmployeeSummary> summaries = generateTenantTimeSummaryUseCase.generate(from, to);
        return renderer.render(summaries, from, to);
    }
}
