package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Modelo de dominio de un refresh token opaco persistido en BD. La rotacion y
 * revocacion viven aqui para mantener las reglas de sesion fuera del
 * controlador.
 */
public final class RefreshToken {

    private final UUID id;
    private final UUID userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant revokedAt;
    private UUID replacedBy;
    private final Instant createdAt;

    private RefreshToken(
            UUID id,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Instant revokedAt,
            UUID replacedBy,
            Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.replacedBy = replacedBy;
        this.createdAt = createdAt;
    }

    public static RefreshToken issue(
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Clock clock,
            IdGenerator idGenerator) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        Objects.requireNonNull(userId, "userId no puede ser null");
        String validatedHash = requireNonBlank(tokenHash, "tokenHash no puede ser blank");
        Objects.requireNonNull(expiresAt, "expiresAt no puede ser null");

        Instant now = clock.now();
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt debe ser posterior a createdAt");
        }

        return new RefreshToken(idGenerator.newId(), userId, validatedHash, expiresAt, null, null, now);
    }

    public static RefreshToken reconstitute(
            UUID id,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Instant revokedAt,
            UUID replacedBy,
            Instant createdAt) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(userId, "userId no puede ser null");
        Objects.requireNonNull(expiresAt, "expiresAt no puede ser null");
        Objects.requireNonNull(createdAt, "createdAt no puede ser null");
        return new RefreshToken(id, userId, requireNonBlank(tokenHash, "tokenHash no puede ser blank"), expiresAt, revokedAt,
                replacedBy, createdAt);
    }

    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant no puede ser null");
        return !expiresAt.isAfter(instant);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt no puede ser null");
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }

    public void rotateTo(UUID replacementId, Instant revokedAt) {
        Objects.requireNonNull(replacementId, "replacementId no puede ser null");
        revoke(revokedAt);
        this.replacedBy = replacementId;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public UUID replacedBy() {
        return replacedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
