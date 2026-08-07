package com.tfp.timetracking.reporting.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado por empleado en un rango de fechas, sin desglose diario (T801,
 * {@code GET /api/v1/reports/tenant/summary} y {@code .../export.csv}, que
 * exponen el mismo dato).
 *
 * <p>A diferencia de {@link EmployeeDaySummary}, este total no depende de la
 * zona horaria del tenant: la suma de los segmentos diarios de una jornada
 * es igual a la duracion completa del intervalo, independientemente de donde
 * caigan los limites de dia (ver {@code TimeSummaryCalculator}).
 */
public record TenantEmployeeSummary(
        UUID employeeId,
        Duration worked,
        Duration paused,
        Duration expected,
        Duration effectiveWorked,
        Duration overtime,
        Duration deviation,
        int workdayCount,
        int adjustedWorkdayCount,
        int openWorkdays,
        int evaluatedWorkdayCount) {

    public TenantEmployeeSummary {
        Objects.requireNonNull(employeeId, "employeeId no puede ser null");
        Objects.requireNonNull(worked, "worked no puede ser null");
        Objects.requireNonNull(paused, "paused no puede ser null");
        Objects.requireNonNull(expected, "expected no puede ser null");
        Objects.requireNonNull(effectiveWorked, "effectiveWorked no puede ser null");
        Objects.requireNonNull(overtime, "overtime no puede ser null");
        Objects.requireNonNull(deviation, "deviation no puede ser null");
    }
}
