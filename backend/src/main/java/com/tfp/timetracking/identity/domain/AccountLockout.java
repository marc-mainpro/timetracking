package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado raiz del bloqueo temporal de cuenta (RF-USR-008, RS-008, diseno
 * §8.5). Registra los intentos fallidos consecutivos de un usuario, la fecha
 * del ultimo intento y la fecha de desbloqueo.
 *
 * <p><b>Por que es un agregado propio y no estado dentro de {@link User}</b>
 * (ver ADR-0014): el contador de fallos se escribe en cada login erroneo, un
 * camino de alta frecuencia y controlado por un atacante. Guardarlo en
 * {@code app_user} obligaria a cargar y reescribir la fila del usuario —con su
 * coleccion de roles— en cada intento, y a que dos intentos simultaneos
 * compitiesen por el bloqueo optimista del agregado de usuario, convirtiendo un
 * detalle de seguridad en una fuente de {@code CONCURRENT_MODIFICATION} sobre
 * operaciones de negocio ajenas. Ademas el ciclo de vida es distinto: el
 * bloqueo es un dato operativo y purgable, el usuario no.
 *
 * <p>Su identidad es el {@code userId}: existe como mucho un bloqueo por
 * usuario y no tiene sentido fuera de el.
 *
 * <p>Dominio puro: sin Spring ni JPA. El instante actual llega siempre como
 * parametro {@link Clock}, nunca via {@code Instant.now()}.
 */
public final class AccountLockout {

    private final UUID userId;
    private final UUID tenantId;
    private int failedAttempts;
    private Instant lastFailedAttemptAt;
    private Instant lockedUntil;
    private final Instant createdAt;
    private Instant updatedAt;

    private AccountLockout(
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

    /** Factoria: estado inicial limpio para un usuario sin intentos fallidos. */
    public static AccountLockout start(UUID tenantId, UUID userId, Clock clock) {
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(userId, "userId no puede ser null");
        Objects.requireNonNull(clock, "clock no puede ser null");
        Instant now = clock.now();
        return new AccountLockout(userId, tenantId, 0, null, null, now, now);
    }

    /** Reconstruye el agregado desde persistencia. No es un hecho nuevo. */
    public static AccountLockout reconstitute(
            UUID userId,
            UUID tenantId,
            int failedAttempts,
            Instant lastFailedAttemptAt,
            Instant lockedUntil,
            Instant createdAt,
            Instant updatedAt) {
        Objects.requireNonNull(userId, "userId no puede ser null");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(createdAt, "createdAt no puede ser null");
        Objects.requireNonNull(updatedAt, "updatedAt no puede ser null");
        if (failedAttempts < 0) {
            throw new IllegalArgumentException("failedAttempts no puede ser negativo");
        }
        return new AccountLockout(
                userId, tenantId, failedAttempts, lastFailedAttemptAt, lockedUntil, createdAt, updatedAt);
    }

    /**
     * Registra un intento fallido y bloquea la cuenta si se alcanza el umbral.
     *
     * <p>Los fallos caducan: si el ultimo intento fallido es anterior a
     * {@code failureWindow}, el contador arranca de cero. Sin esa ventana un
     * usuario legitimo acabaria bloqueado por errores tipograficos acumulados a
     * lo largo de semanas.
     *
     * <p>Al bloquear, el contador vuelve a cero: cuando expire el bloqueo la
     * cuenta dispone de un presupuesto completo de intentos otra vez, en lugar
     * de quedar a un solo fallo de volver a bloquearse.
     *
     * @return {@code true} si este intento es el que ha provocado el bloqueo
     */
    public boolean registerFailure(int threshold, Duration lockDuration, Duration failureWindow, Clock clock) {
        Objects.requireNonNull(lockDuration, "lockDuration no puede ser null");
        Objects.requireNonNull(failureWindow, "failureWindow no puede ser null");
        Objects.requireNonNull(clock, "clock no puede ser null");
        if (threshold < 1) {
            throw new IllegalArgumentException("El umbral de intentos fallidos debe ser al menos 1");
        }
        Instant now = clock.now();
        int previous = withinFailureWindow(now, failureWindow) ? failedAttempts : 0;
        this.failedAttempts = previous + 1;
        this.lastFailedAttemptAt = now;
        this.updatedAt = now;
        if (this.failedAttempts >= threshold) {
            this.failedAttempts = 0;
            this.lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    /**
     * Reinicia el bloqueo tras una autenticacion correcta (RS-008: "reinicio
     * tras autenticacion correcta").
     */
    public void registerSuccess(Clock clock) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        this.failedAttempts = 0;
        this.lastFailedAttemptAt = null;
        this.lockedUntil = null;
        this.updatedAt = clock.now();
    }

    /** Indica si la cuenta esta bloqueada en el instante dado por el reloj. */
    public boolean isLocked(Clock clock) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        return lockedUntil != null && clock.now().isBefore(lockedUntil);
    }

    /** {@code true} si el estado en memoria coincide con el de un agregado recien creado. */
    public boolean isPristine() {
        return failedAttempts == 0 && lastFailedAttemptAt == null && lockedUntil == null;
    }

    private boolean withinFailureWindow(Instant now, Duration failureWindow) {
        return lastFailedAttemptAt != null && !lastFailedAttemptAt.plus(failureWindow).isBefore(now);
    }

    public UUID userId() {
        return userId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public Instant lastFailedAttemptAt() {
        return lastFailedAttemptAt;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
