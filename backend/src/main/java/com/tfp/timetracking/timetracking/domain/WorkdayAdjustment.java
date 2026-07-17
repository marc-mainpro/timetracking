package com.tfp.timetracking.timetracking.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record WorkdayAdjustment(Instant startedAt, Instant endedAt, List<AdjustedBreak> breaks) {

    public WorkdayAdjustment {
        Objects.requireNonNull(startedAt, "startedAt no puede ser null");
        Objects.requireNonNull(endedAt, "endedAt no puede ser null");
        Objects.requireNonNull(breaks, "breaks no puede ser null");
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("La jornada ajustada no puede finalizar antes de comenzar");
        }
        breaks = List.copyOf(breaks);
        for (AdjustedBreak breakEntry : breaks) {
            if (breakEntry.startedAt().isBefore(startedAt) || breakEntry.endedAt().isAfter(endedAt)) {
                throw new IllegalArgumentException("Las pausas ajustadas deben quedar dentro de la jornada");
            }
        }
    }

    public record AdjustedBreak(Instant startedAt, Instant endedAt) {

        public AdjustedBreak {
            Objects.requireNonNull(startedAt, "startedAt no puede ser null");
            Objects.requireNonNull(endedAt, "endedAt no puede ser null");
            if (endedAt.isBefore(startedAt)) {
                throw new IllegalArgumentException("La pausa ajustada no puede finalizar antes de comenzar");
            }
        }
    }
}
