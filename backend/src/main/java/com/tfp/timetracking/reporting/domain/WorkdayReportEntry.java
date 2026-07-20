package com.tfp.timetracking.reporting.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Proyeccion minima de una jornada para el calculo de informes (T801).
 *
 * <p>No es el agregado {@code Workday}: solo lleva los campos que el
 * calculo de informes necesita, obtenidos por
 * {@link WorkdaySummaryQueryPort} con una consulta de agregacion directa
 * (ver Javadoc del puerto para la justificacion arquitectonica).
 *
 * @param open     {@code true} si la jornada esta {@code OPEN} o
 *                 {@code ON_BREAK} (sigue en curso). Las jornadas abiertas se
 *                 excluyen del tiempo trabajado/pausado total (decision de
 *                 negocio T801) pero se cuentan en {@code openWorkdays}.
 * @param adjusted {@code true} si la jornada esta en estado {@code ADJUSTED}.
 * @param endedAt  puede ser {@code null} solo si {@code open} es
 *                 {@code true}.
 */
public record WorkdayReportEntry(
        UUID workdayId, UUID employeeId, boolean open, boolean adjusted, Instant startedAt, Instant endedAt, List<BreakInterval> breaks) {

    public WorkdayReportEntry {
        Objects.requireNonNull(workdayId, "workdayId no puede ser null");
        Objects.requireNonNull(employeeId, "employeeId no puede ser null");
        Objects.requireNonNull(startedAt, "startedAt no puede ser null");
        if (!open && endedAt == null) {
            throw new IllegalArgumentException("Una jornada cerrada/ajustada debe tener endedAt");
        }
        breaks = List.copyOf(Objects.requireNonNull(breaks, "breaks no puede ser null"));
    }
}
