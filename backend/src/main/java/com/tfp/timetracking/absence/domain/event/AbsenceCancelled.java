package com.tfp.timetracking.absence.domain.event;

import java.time.Instant;
import java.util.UUID;

public record AbsenceCancelled(
        UUID eventId,
        Instant occurredAt,
        UUID tenantId,
        UUID aggregateId,
        UUID employeeId) {}
