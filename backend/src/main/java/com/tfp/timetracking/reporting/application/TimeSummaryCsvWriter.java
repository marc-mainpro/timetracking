package com.tfp.timetracking.reporting.application;

import com.tfp.timetracking.reporting.domain.EmployeeName;
import com.tfp.timetracking.reporting.domain.TenantEmployeeSummary;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Formateador CSV puro (sin Spring) para el informe agregado de tenant
 * (T801, {@code GET /api/v1/reports/tenant/export.csv}).
 *
 * <p>Formato: cabecera en la primera linea, separador coma, fin de linea
 * {@code \r\n} (RFC 4180), y campos escapados entre comillas dobles cuando
 * contienen coma, comilla o salto de linea (comillas dobladas dentro del
 * valor). Las duraciones se exportan como segundos enteros (columna
 * {@code *Seconds}) para que cualquier hoja de calculo o script las importe
 * sin ambiguedad, en vez de un formato de texto tipo {@code PT8H30M}.
 *
 * <p>Codificacion: UTF-8 sin BOM. Se documenta como decision explicita
 * (CONTEXT-API §2 permite BOM opcional): los consumidores objetivo (import
 * programatico, Excel moderno) leen UTF-8 sin BOM correctamente; anadir BOM
 * solo aporta valor en escenarios de doble clic en Excel antiguo de Windows,
 * fuera de alcance del MVP.
 */
public final class TimeSummaryCsvWriter {

    private static final String LINE_SEPARATOR = "\r\n";
    private static final String[] HEADERS = {
        "lastName",
        "firstName",
        "workedSeconds",
        "pausedSeconds",
        "expectedSeconds",
        "effectiveWorkedSeconds",
        "overtimeSeconds",
        "deviationSeconds",
        "workdayCount",
        "adjustedWorkdayCount",
        "openWorkdays",
        "evaluatedWorkdayCount"
    };

    private TimeSummaryCsvWriter() {}

    /**
     * @param names nombre de cada empleado del tenant, resuelto por
     *     {@link com.tfp.timetracking.reporting.domain.EmployeeDirectoryPort}.
     *     Un empleado sin entrada (id no encontrado) exporta las columnas de
     *     nombre vacias en vez de romper la exportacion.
     */
    public static String write(List<TenantEmployeeSummary> summaries, Map<UUID, EmployeeName> names) {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", HEADERS)).append(LINE_SEPARATOR);
        for (TenantEmployeeSummary summary : summaries) {
            csv.append(toRow(summary, names.get(summary.employeeId()))).append(LINE_SEPARATOR);
        }
        return csv.toString();
    }

    private static String toRow(TenantEmployeeSummary summary, EmployeeName name) {
        return String.join(
                ",",
                escape(name != null ? name.lastName() : ""),
                escape(name != null ? name.firstName() : ""),
                escape(Long.toString(summary.worked().getSeconds())),
                escape(Long.toString(summary.paused().getSeconds())),
                escape(Long.toString(summary.expected().getSeconds())),
                escape(Long.toString(summary.effectiveWorked().getSeconds())),
                escape(Long.toString(summary.overtime().getSeconds())),
                escape(Long.toString(summary.deviation().getSeconds())),
                escape(Integer.toString(summary.workdayCount())),
                escape(Integer.toString(summary.adjustedWorkdayCount())),
                escape(Integer.toString(summary.openWorkdays())),
                escape(Integer.toString(summary.evaluatedWorkdayCount())));
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
