package com.tfp.timetracking.absence.domain.event;

import java.time.Instant;
import java.util.UUID;

public record AbsenceRejected(
        UUID eventId,
        Instant occurredAt,
        UUID tenantId,
        UUID aggregateId,
        UUID employeeId,
        UUID resolvedBy) {}
