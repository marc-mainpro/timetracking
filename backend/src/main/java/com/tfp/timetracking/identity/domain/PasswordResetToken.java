package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.identity.domain.event.PasswordResetRequested;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PasswordResetToken {

    private final UUID id;
    private final UUID tenantId;
    private final UUID userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant usedAt;
    private final Instant createdAt;
    private final List<Object> domainEvents = new ArrayList<>();

    private PasswordResetToken(
            UUID id,
            UUID tenantId,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Instant usedAt,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.createdAt = createdAt;
    }

    public static PasswordResetToken issue(
            User user,
            GeneratedPasswordResetToken generatedToken,
            Duration ttl,
            Clock clock,
            IdGenerator idGenerator) {
        Objects.requireNonNull(user, "user no puede ser null");
        Objects.requireNonNull(generatedToken, "generatedToken no puede ser null");
        Objects.requireNonNull(ttl, "ttl no puede ser null");
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        Instant now = clock.now();
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl debe ser positiva");
        }
        UUID tokenId = idGenerator.newId();
        PasswordResetToken token = new PasswordResetToken(
                tokenId,
                user.tenantId(),
                user.id(),
                generatedToken.hash(),
                now.plus(ttl),
                null,
                now);
        token.domainEvents.add(new PasswordResetRequested(
                idGenerator.newId(),
                now,
                user.tenantId(),
                tokenId,
                user.id(),
                user.email().value(),
                user.firstName(),
                generatedToken.value(),
                token.expiresAt));
        return token;
    }

    public static PasswordResetToken reconstitute(
            UUID id,
            UUID tenantId,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Instant usedAt,
            Instant createdAt) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(userId, "userId no puede ser null");
        Objects.requireNonNull(expiresAt, "expiresAt no puede ser null");
        Objects.requireNonNull(createdAt, "createdAt no puede ser null");
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash no puede ser blank");
        }
        return new PasswordResetToken(id, tenantId, userId, tokenHash, expiresAt, usedAt, createdAt);
    }

    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant no puede ser null");
        return !expiresAt.isAfter(instant);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void consume(Instant instant) {
        Objects.requireNonNull(instant, "instant no puede ser null");
        if (isUsed() || isExpiredAt(instant)) {
            throw new InvalidPasswordResetTokenException();
        }
        this.usedAt = instant;
    }

    public void invalidate(Instant instant) {
        Objects.requireNonNull(instant, "instant no puede ser null");
        if (usedAt == null) {
            this.usedAt = instant;
        }
    }

    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
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

    public Instant usedAt() {
        return usedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
