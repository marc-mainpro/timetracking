package com.tfp.timetracking.timetracking.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public record HourlyRules(UUID tenantId, Duration maxDailyWork, Duration requiredBreak) {

    public HourlyRules {
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        if (maxDailyWork != null && (maxDailyWork.isZero() || maxDailyWork.isNegative())) {
            throw new IllegalArgumentException("maxDailyWork debe ser positiva");
        }
        if (requiredBreak != null && requiredBreak.isNegative()) {
            throw new IllegalArgumentException("requiredBreak no puede ser negativa");
        }
    }

    public static HourlyRules withoutLimits(UUID tenantId) {
        return new HourlyRules(tenantId, null, null);
    }
}
