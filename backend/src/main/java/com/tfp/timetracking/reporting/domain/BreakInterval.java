package com.tfp.timetracking.reporting.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Intervalo de una pausa ya finalizada, para el calculo de informes (T801).
 * Solo se modelan pausas cerradas: una jornada {@code CLOSED}/{@code ADJUSTED}
 * nunca tiene pausas abiertas (invariante de {@code Workday.close}).
 */
public record BreakInterval(Instant startedAt, Instant endedAt) {

    public BreakInterval {
        Objects.requireNonNull(startedAt, "startedAt no puede ser null");
        Objects.requireNonNull(endedAt, "endedAt no puede ser null");
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("La pausa no puede finalizar antes de comenzar");
        }
    }
}
