package com.tfp.timetracking.reporting.application;

import com.tfp.timetracking.reporting.domain.TenantEmployeeSummary;
import java.util.List;

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
        "employeeId", "workedSeconds", "pausedSeconds", "workdayCount", "adjustedWorkdayCount", "openWorkdays"
    };

    private TimeSummaryCsvWriter() {}

    public static String write(List<TenantEmployeeSummary> summaries) {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", HEADERS)).append(LINE_SEPARATOR);
        for (TenantEmployeeSummary summary : summaries) {
            csv.append(toRow(summary)).append(LINE_SEPARATOR);
        }
        return csv.toString();
    }

    private static String toRow(TenantEmployeeSummary summary) {
        return String.join(
                ",",
                escape(summary.employeeId().toString()),
                escape(Long.toString(summary.worked().getSeconds())),
                escape(Long.toString(summary.paused().getSeconds())),
                escape(Integer.toString(summary.workdayCount())),
                escape(Integer.toString(summary.adjustedWorkdayCount())),
                escape(Integer.toString(summary.openWorkdays())));
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
