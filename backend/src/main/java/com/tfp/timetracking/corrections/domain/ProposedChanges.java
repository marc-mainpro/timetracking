package com.tfp.timetracking.corrections.domain;

import com.tfp.timetracking.timetracking.domain.WorkdayAdjustment;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ProposedChanges(Instant startedAt, Instant endedAt, List<ProposedBreak> breaks) {

    public ProposedChanges {
        Objects.requireNonNull(startedAt, "startedAt no puede ser null");
        Objects.requireNonNull(endedAt, "endedAt no puede ser null");
        Objects.requireNonNull(breaks, "breaks no puede ser null");
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("La jornada propuesta no puede finalizar antes de comenzar");
        }
        breaks = List.copyOf(breaks);
        for (ProposedBreak proposedBreak : breaks) {
            if (proposedBreak.startedAt().isBefore(startedAt) || proposedBreak.endedAt().isAfter(endedAt)) {
                throw new IllegalArgumentException("Las pausas propuestas deben quedar dentro de la jornada");
            }
        }
    }

    public WorkdayAdjustment toWorkdayAdjustment() {
        return new WorkdayAdjustment(
                startedAt,
                endedAt,
                breaks.stream().map(breakEntry -> new WorkdayAdjustment.AdjustedBreak(breakEntry.startedAt(), breakEntry.endedAt())).toList());
    }

    public record ProposedBreak(Instant startedAt, Instant endedAt) {

        public ProposedBreak {
            Objects.requireNonNull(startedAt, "startedAt no puede ser null");
            Objects.requireNonNull(endedAt, "endedAt no puede ser null");
            if (endedAt.isBefore(startedAt)) {
                throw new IllegalArgumentException("La pausa propuesta no puede finalizar antes de comenzar");
            }
        }
    }
}
