package com.tfp.timetracking.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        UUID tenantId,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        UUID correlationId,
        Map<String, Object> metadata,
        Instant occurredAt) {}
