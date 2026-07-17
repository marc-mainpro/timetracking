package com.tfp.timetracking.corrections.domain.event;

import java.time.Instant;
import java.util.UUID;

public record CorrectionRejected(
        UUID eventId, Instant occurredAt, UUID tenantId, UUID aggregateId, UUID workdayId, UUID resolvedBy) {}
