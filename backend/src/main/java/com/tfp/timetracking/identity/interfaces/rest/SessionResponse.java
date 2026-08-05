package com.tfp.timetracking.identity.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id, Instant createdAt, Instant lastUsedAt, Instant expiresAt, Instant revokedAt, boolean current) {}
