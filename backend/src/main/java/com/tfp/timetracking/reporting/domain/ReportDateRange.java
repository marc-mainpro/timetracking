package com.tfp.timetracking.reporting.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Rango de fechas validado para un informe (CONTEXT-API §2 / T801).
 *
 * <p>Invariantes: {@code from} y {@code to} son obligatorios, {@code from}
 * no puede ser posterior a {@code to}, y el rango no puede superar 366 dias
 * (limite de negocio para acotar el coste de las consultas de agregacion).
 * Ambas violaciones lanzan {@link IllegalArgumentException}, que el
 * {@code GlobalExceptionHandler} traduce a {@code 400}.
 */
public record ReportDateRange(Instant from, Instant to) {

    private static final long MAX_RANGE_DAYS = 366;

    public ReportDateRange {
        Objects.requireNonNull(from, "from no puede ser null");
        Objects.requireNonNull(to, "to no puede ser null");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' no puede ser anterior a 'from'");
        }
        if (Duration.between(from, to).toDays() > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("El rango de fechas no puede superar " + MAX_RANGE_DAYS + " dias");
        }
    }
}
