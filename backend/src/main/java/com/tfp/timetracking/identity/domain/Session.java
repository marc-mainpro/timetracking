package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Session {

    private final UUID id;
    private final UUID userId;
    private final UUID tenantId;
    private final Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;
    private Instant revokedAt;
    private final String userAgentHash;
    private final String ipHash;

    private Session(
            UUID id,
            UUID userId,
            UUID tenantId,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt,
            Instant revokedAt,
            String userAgentHash,
            String ipHash) {
        this.id = id;
        this.userId = userId;
        this.tenantId = tenantId;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.userAgentHash = userAgentHash;
        this.ipHash = ipHash;
    }

    public static Session start(UUID userId, UUID tenantId, Instant expiresAt, Clock clock, IdGenerator idGenerator) {
        Objects.requireNonNull(userId, "userId no puede ser null");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(expiresAt, "expiresAt no puede ser null");
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        Instant now = clock.now();
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt debe ser posterior a createdAt");
        }
        return new Session(idGenerator.newId(), userId, tenantId, now, now, expiresAt, null, null, null);
    }

    public static Session reconstitute(
            UUID id,
            UUID userId,
            UUID tenantId,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt,
            Instant revokedAt,
            String userAgentHash,
            String ipHash) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(userId, "userId no puede ser null");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(createdAt, "createdAt no puede ser null");
        Objects.requireNonNull(lastUsedAt, "lastUsedAt no puede ser null");
        Objects.requireNonNull(expiresAt, "expiresAt no puede ser null");
        return new Session(id, userId, tenantId, createdAt, lastUsedAt, expiresAt, revokedAt, userAgentHash, ipHash);
    }

    public void touch(Instant usedAt, Instant newExpiresAt) {
        Objects.requireNonNull(usedAt, "usedAt no puede ser null");
        Objects.requireNonNull(newExpiresAt, "newExpiresAt no puede ser null");
        this.lastUsedAt = usedAt;
        this.expiresAt = newExpiresAt;
    }

    public void revoke(Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt no puede ser null");
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant no puede ser null");
        return !expiresAt.isAfter(instant);
    }

    public boolean isActiveAt(Instant instant) {
        return !isRevoked() && !isExpiredAt(instant);
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastUsedAt() {
        return lastUsedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public String userAgentHash() {
        return userAgentHash;
    }

    public String ipHash() {
        return ipHash;
    }
}
