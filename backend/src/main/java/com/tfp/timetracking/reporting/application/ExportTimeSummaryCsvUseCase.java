package com.tfp.timetracking.reporting.application;

import com.tfp.timetracking.reporting.domain.EmployeeDirectoryPort;
import com.tfp.timetracking.reporting.domain.EmployeeName;
import com.tfp.timetracking.shared.application.TenantContext;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Exportacion CSV del informe agregado de tenant (T801,
 * {@code GET /api/v1/reports/tenant/export.csv}). Reutiliza
 * {@link GenerateTenantTimeSummaryUseCase} para obtener exactamente el mismo
 * dato que {@code GET /api/v1/reports/tenant/summary} (CONTEXT-API §2: "mismo
 * filtro, text/csv") y solo cambia el formato de salida.
 *
 * <p>El nombre del empleado se resuelve aqui, no en
 * {@link GenerateTenantTimeSummaryUseCase}: la vista JSON de pantalla ya
 * resuelve el nombre en el cliente a partir del listado de empleados, así que
 * solo las exportaciones a fichero (sin postprocesado posible) necesitan
 * incrustarlo.
 */
@Service
public class ExportTimeSummaryCsvUseCase {

    private final GenerateTenantTimeSummaryUseCase generateTenantTimeSummaryUseCase;
    private final EmployeeDirectoryPort employeeDirectoryPort;
    private final TenantContext tenantContext;

    public ExportTimeSummaryCsvUseCase(
            GenerateTenantTimeSummaryUseCase generateTenantTimeSummaryUseCase,
            EmployeeDirectoryPort employeeDirectoryPort,
            TenantContext tenantContext) {
        this.generateTenantTimeSummaryUseCase = generateTenantTimeSummaryUseCase;
        this.employeeDirectoryPort = employeeDirectoryPort;
        this.tenantContext = tenantContext;
    }

    public String export(Instant from, Instant to) {
        Map<UUID, EmployeeName> names = employeeDirectoryPort.namesByTenant(tenantContext.currentTenantId());
        return TimeSummaryCsvWriter.write(generateTenantTimeSummaryUseCase.generate(from, to), names);
    }
}
