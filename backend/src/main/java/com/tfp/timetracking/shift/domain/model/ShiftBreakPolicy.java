package com.tfp.timetracking.shift.domain.model;

import java.time.Duration;

public record ShiftBreakPolicy(Duration plannedBreakDuration) {

    public ShiftBreakPolicy {
        if (plannedBreakDuration == null) {
            plannedBreakDuration = Duration.ZERO;
        }
        if (plannedBreakDuration.isNegative()) {
            throw new IllegalArgumentException("La pausa prevista no puede ser negativa");
        }
    }
}
