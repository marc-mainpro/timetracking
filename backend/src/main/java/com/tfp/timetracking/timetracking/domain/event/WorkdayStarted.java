package com.tfp.timetracking.timetracking.domain.event;

import java.time.Instant;
import java.util.UUID;

public record WorkdayStarted(
        UUID eventId, Instant occurredAt, UUID tenantId, UUID aggregateId, UUID employeeId, Instant startedAt) {}
