package com.tfp.timetracking.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA de {@code account_lockout} (V14__account_lockout.sql). La clave
 * primaria es el {@code user_id}: hay como mucho una fila de bloqueo por
 * usuario.
 *
 * <p>Sin {@code @Version} a proposito (ADR-0013): dos intentos fallidos
 * simultaneos no deben producir un fallo de bloqueo optimista en el camino de
 * login. El unico efecto de la escritura concurrente es perder un incremento
 * del contador, lo que como mucho concede un intento extra al atacante.
 */
@Entity
@Table(name = "account_lockout")
public class AccountLockoutJpaEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "last_failed_attempt_at")
    private Instant lastFailedAttemptAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountLockoutJpaEntity() {
        // Requerido por JPA.
    }

    public AccountLockoutJpaEntity(
            UUID userId,
            UUID tenantId,
            int failedAttempts,
            Instant lastFailedAttemptAt,
            Instant lockedUntil,
            Instant createdAt,
            Instant updatedAt) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.failedAttempts = failedAttempts;
        this.lastFailedAttemptAt = lastFailedAttemptAt;
        this.lockedUntil = lockedUntil;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLastFailedAttemptAt() {
        return lastFailedAttemptAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
