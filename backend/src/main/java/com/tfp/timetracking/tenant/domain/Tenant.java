package com.tfp.timetracking.tenant.domain;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.domain.event.TenantRegistered;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado raiz Tenant (CONTEXT-DOMINIO §1). Modelo de dominio puro: sin
 * anotaciones de Spring ni de JPA (persistencia en
 * {@code tenant.infrastructure.persistence.TenantJpaEntity}).
 *
 * <p>Reglas: nombre obligatorio; timezone IANA valida; un tenant inactivo no
 * puede operar (la comprobacion de "operar" la hacen los casos de uso de
 * otros modulos consultando {@link #isActive()}).
 */
public final class Tenant {

    private final UUID id;
    private String name;
    private TenantStatus status;
    private String timezone;
    private final Instant createdAt;
    private Instant updatedAt;

    private final List<Object> domainEvents = new ArrayList<>();

    private Tenant(UUID id, String name, TenantStatus status, String timezone, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.timezone = timezone;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factoria: registra un nuevo tenant activo. Valida que el nombre no sea
     * vacio y que la zona horaria sea un identificador IANA valido (parseable
     * con {@link ZoneId#of(String)}).
     */
    public static Tenant register(String name, String timezone, Clock clock, IdGenerator idGenerator) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        String validatedName = validateName(name);
        String validatedTimezone = validateTimezone(timezone);

        UUID id = idGenerator.newId();
        Instant now = clock.now();
        Tenant tenant = new Tenant(id, validatedName, TenantStatus.ACTIVE, validatedTimezone, now, now);
        tenant.domainEvents.add(new TenantRegistered(idGenerator.newId(), now, id, id, validatedName, validatedTimezone));
        return tenant;
    }

    /**
     * Reconstruye un Tenant existente desde persistencia. No genera eventos
     * de dominio (no es un hecho nuevo).
     */
    public static Tenant reconstitute(
            UUID id, String name, TenantStatus status, String timezone, Instant createdAt, Instant updatedAt) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(status, "status no puede ser null");
        Objects.requireNonNull(createdAt, "createdAt no puede ser null");
        Objects.requireNonNull(updatedAt, "updatedAt no puede ser null");
        return new Tenant(id, validateName(name), status, validateTimezone(timezone), createdAt, updatedAt);
    }

    public void deactivate(Clock clock) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        this.status = TenantStatus.INACTIVE;
        this.updatedAt = clock.now();
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

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del tenant es obligatorio");
        }
        return name.trim();
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
}
