package com.tfp.timetracking.reporting.application;

import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Exportacion CSV del informe agregado de tenant (T801,
 * {@code GET /api/v1/reports/tenant/export.csv}). Reutiliza
 * {@link GenerateTenantTimeSummaryUseCase} para obtener exactamente el mismo
 * dato que {@code GET /api/v1/reports/tenant/summary} (CONTEXT-API §2: "mismo
 * filtro, text/csv") y solo cambia el formato de salida.
 */
@Service
public class ExportTimeSummaryCsvUseCase {

    private final GenerateTenantTimeSummaryUseCase generateTenantTimeSummaryUseCase;

    public ExportTimeSummaryCsvUseCase(GenerateTenantTimeSummaryUseCase generateTenantTimeSummaryUseCase) {
        this.generateTenantTimeSummaryUseCase = generateTenantTimeSummaryUseCase;
    }

    public String export(Instant from, Instant to) {
        return TimeSummaryCsvWriter.write(generateTenantTimeSummaryUseCase.generate(from, to));
    }
}
