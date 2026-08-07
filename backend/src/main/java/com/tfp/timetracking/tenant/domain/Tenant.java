package com.tfp.timetracking.tenant.domain;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.domain.event.TenantActivated;
import com.tfp.timetracking.tenant.domain.event.TenantArchived;
import com.tfp.timetracking.tenant.domain.event.TenantReactivated;
import com.tfp.timetracking.tenant.domain.event.TenantRegistered;
import com.tfp.timetracking.tenant.domain.event.TenantSuspended;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado raíz Tenant (CONTEXT-DOMINIO §1). Modelo de dominio puro: sin
 * anotaciones de Spring ni de JPA (persistencia en
 * {@code tenant.infrastructure.persistence.TenantJpaEntity}).
 *
 * <p>Reglas: nombre obligatorio; timezone IANA válida; un tenant no
 * {@code ACTIVE} no puede operar (la comprobación de "operar" la hacen los
 * casos de uso de otros módulos consultando {@link #isActive()}).
 *
 * <p>Ciclo de vida V2 (RF-TEN-004, diseño §7.2): un tenant se crea
 * {@code ACTIVE} vía {@link #register} (creación directa) o {@code PENDING}
 * vía {@link #requestRegistration} (registro público controlado). Las
 * transiciones {@link #activate}, {@link #suspend}, {@link #reactivate} y
 * {@link #archive} validan el estado de origen y emiten el evento de dominio
 * correspondiente.
 */
public final class Tenant {

    private final UUID id;
    private String name;
    private TenantStatus status;
    private String timezone;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant activatedAt;
    private Instant suspendedAt;
    private Instant archivedAt;
    private String suspensionReason;

    private final List<Object> domainEvents = new ArrayList<>();

    private Tenant(
            UUID id,
            String name,
            TenantStatus status,
            String timezone,
            Instant createdAt,
            Instant updatedAt,
            Instant activatedAt,
            Instant suspendedAt,
            Instant archivedAt,
            String suspensionReason) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.timezone = timezone;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.activatedAt = activatedAt;
        this.suspendedAt = suspendedAt;
        this.archivedAt = archivedAt;
        this.suspensionReason = suspensionReason;
    }

    /**
     * Factoría: registra un nuevo tenant ya {@code ACTIVE} (creación directa,
     * p.ej. alta manual por {@code PLATFORM_ADMIN} o registro directo cuando
     * la verificación de correo no es obligatoria). Emite {@link TenantRegistered}.
     */
    public static Tenant register(String name, String timezone, Clock clock, IdGenerator idGenerator) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        String validatedName = validateName(name);
        String validatedTimezone = validateTimezone(timezone);

        UUID id = idGenerator.newId();
        Instant now = clock.now();
        Tenant tenant =
                new Tenant(id, validatedName, TenantStatus.ACTIVE, validatedTimezone, now, now, now, null, null, null);
        tenant.domainEvents.add(new TenantRegistered(idGenerator.newId(), now, id, id, validatedName, validatedTimezone));
        return tenant;
    }

    /**
     * Factoría: crea un tenant en estado {@code PENDING} a partir de una
     * solicitud de registro público controlado (RF-TEN-003, T53-03). No emite
     * {@link TenantRegistered}: el hecho relevante es la solicitud; el tenant
     * operativo nace al activarse.
     */
    public static Tenant requestRegistration(String name, String timezone, Clock clock, IdGenerator idGenerator) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        String validatedName = validateName(name);
        String validatedTimezone = validateTimezone(timezone);

        UUID id = idGenerator.newId();
        Instant now = clock.now();
        return new Tenant(id, validatedName, TenantStatus.PENDING, validatedTimezone, now, now, null, null, null, null);
    }

    /**
     * Reconstruye un Tenant existente desde persistencia. No genera eventos
     * de dominio (no es un hecho nuevo).
     */
    public static Tenant reconstitute(
            UUID id,
            String name,
            TenantStatus status,
            String timezone,
            Instant createdAt,
            Instant updatedAt,
            Instant activatedAt,
            Instant suspendedAt,
            Instant archivedAt,
            String suspensionReason) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(status, "status no puede ser null");
        Objects.requireNonNull(createdAt, "createdAt no puede ser null");
        Objects.requireNonNull(updatedAt, "updatedAt no puede ser null");
        return new Tenant(
                id,
                validateName(name),
                status,
                validateTimezone(timezone),
                createdAt,
                updatedAt,
                activatedAt,
                suspendedAt,
                archivedAt,
                suspensionReason);
    }

    /** Activa un tenant {@code PENDING} (RF-TEN-005). Emite {@link TenantActivated}. */
    public void activate(Clock clock, IdGenerator idGenerator) {
        requireStatus(TenantStatus.PENDING, "activar");
        Instant now = touch(clock);
        this.status = TenantStatus.ACTIVE;
        this.activatedAt = now;
        this.domainEvents.add(new TenantActivated(idGenerator.newId(), now, id, id));
    }

    /** Suspende un tenant {@code ACTIVE} con motivo obligatorio (RF-TEN-006). */
    public void suspend(String reason, Clock clock, IdGenerator idGenerator) {
        requireStatus(TenantStatus.ACTIVE, "suspender");
        String validatedReason = validateReason(reason);
        Instant now = touch(clock);
        this.status = TenantStatus.SUSPENDED;
        this.suspendedAt = now;
        this.suspensionReason = validatedReason;
        this.domainEvents.add(new TenantSuspended(idGenerator.newId(), now, id, id, validatedReason));
    }

    /** Reactiva un tenant {@code SUSPENDED} (RF-TEN-007). Emite {@link TenantReactivated}. */
    public void reactivate(Clock clock, IdGenerator idGenerator) {
        requireStatus(TenantStatus.SUSPENDED, "reactivar");
        Instant now = touch(clock);
        this.status = TenantStatus.ACTIVE;
        this.activatedAt = now;
        this.suspensionReason = null;
        this.domainEvents.add(new TenantReactivated(idGenerator.newId(), now, id, id));
    }

    /**
     * Archiva un tenant {@code ACTIVE} o {@code SUSPENDED} de forma permanente
     * (RF-TEN-008). El motivo es opcional. Emite {@link TenantArchived}.
     */
    public void archive(String reason, Clock clock, IdGenerator idGenerator) {
        if (status != TenantStatus.ACTIVE && status != TenantStatus.SUSPENDED) {
            throw new IllegalTenantTransitionException(status, "archivar");
        }
        String normalizedReason = reason == null || reason.isBlank() ? null : reason.trim();
        Instant now = touch(clock);
        this.status = TenantStatus.ARCHIVED;
        this.archivedAt = now;
        this.domainEvents.add(new TenantArchived(idGenerator.newId(), now, id, id, normalizedReason));
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }

    /** Devuelve y limpia los eventos de dominio acumulados por el agregado. */
    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void requireStatus(TenantStatus required, String transition) {
        if (status != required) {
            throw new IllegalTenantTransitionException(status, transition);
        }
    }

    private Instant touch(Clock clock) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        Instant now = clock.now();
        this.updatedAt = now;
        return now;
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del tenant es obligatorio");
        }
        return name.trim();
    }

    private static String validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        return reason.trim();
    }

    private static String validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("La zona horaria del tenant es obligatoria");
        }
        try {
            ZoneId.of(timezone);
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException("Zona horaria IANA invalida: " + timezone, e);
        }
        return timezone;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public TenantStatus status() {
        return status;
    }

    public String timezone() {
        return timezone;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant activatedAt() {
        return activatedAt;
    }

    public Instant suspendedAt() {
        return suspendedAt;
    }

    public Instant archivedAt() {
        return archivedAt;
    }

    public String suspensionReason() {
        return suspensionReason;
    }
}
