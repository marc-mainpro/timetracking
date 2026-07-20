package com.tfp.timetracking.reporting.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Resumen de un dia natural (en la zona horaria del tenant) para un
 * empleado (T801, {@code GET /api/v1/reports/employees/{employeeId}/summary}).
 *
 * @param day                 dia natural en la zona del tenant.
 * @param worked              tiempo trabajado ese dia (jornada menos pausas),
 *                            excluyendo jornadas todavia abiertas.
 * @param paused              tiempo total de pausa ese dia.
 * @param workdayCount        numero de jornadas cuyo inicio (hora local del
 *                            tenant) cae en este dia, incluyendo abiertas.
 * @param adjustedWorkdayCount de las anteriores, cuantas estan en estado
 *                            {@code ADJUSTED}.
 * @param openWorkdays        de las anteriores, cuantas siguen abiertas
 *                            ({@code OPEN}/{@code ON_BREAK}); excluidas de
 *                            {@code worked}/{@code paused}.
 */
public record EmployeeDaySummary(
        LocalDate day, Duration worked, Duration paused, int workdayCount, int adjustedWorkdayCount, int openWorkdays) {

    public EmployeeDaySummary {
        Objects.requireNonNull(day, "day no puede ser null");
        Objects.requireNonNull(worked, "worked no puede ser null");
        Objects.requireNonNull(paused, "paused no puede ser null");
    }
}
