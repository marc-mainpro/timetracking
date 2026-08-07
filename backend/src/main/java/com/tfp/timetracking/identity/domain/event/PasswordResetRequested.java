package com.tfp.timetracking.identity.domain.event;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetRequested(
        UUID eventId,
        Instant occurredAt,
        UUID tenantId,
        UUID aggregateId,
        UUID userId,
        String email,
        String firstName,
        String resetToken,
        Instant expiresAt) {}
