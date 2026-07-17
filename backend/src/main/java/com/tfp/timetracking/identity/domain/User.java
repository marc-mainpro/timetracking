package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.identity.domain.event.EmployeeCreated;
import com.tfp.timetracking.identity.domain.event.EmployeeDeactivated;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agregado raiz User (CONTEXT-DOMINIO §1). Modelo de dominio puro: sin
 * anotaciones de Spring ni de JPA (persistencia en
 * {@code identity.infrastructure.persistence.UserJpaEntity}).
 *
 * <p>Reglas: email unico dentro del tenant (comprobado por el caso de uso via
 * {@link UserRepository#existsByTenantIdAndEmail}, no aqui); usuario inactivo
 * no se autentica; un usuario pertenece a un unico tenant.
 */
public final class User {

    private final UUID id;
    private final UUID tenantId;
    private Email email;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private UserStatus status;
    private Set<Role> roles;
    private final Instant createdAt;
    private Instant updatedAt;

    private final List<Object> domainEvents = new ArrayList<>();

    private User(
            UUID id,
            UUID tenantId,
            Email email,
            String passwordHash,
            String firstName,
            String lastName,
            UserStatus status,
            Set<Role> roles,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
        this.roles = roles;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factoria: crea un nuevo usuario activo dentro de un tenant. Valida que
     * el email tenga formato correcto (normalizado a minusculas por
     * {@link Email#of(String)}) y que se asigne al menos un rol. La unicidad
     * de email dentro del tenant es responsabilidad del caso de uso
     * (consulta previa al puerto {@link UserRepository}), no de la factoria.
     */
    public static User create(
            UUID tenantId,
            String rawEmail,
            String passwordHash,
            String firstName,
            String lastName,
            Set<Role> roles,
            Clock clock,
            IdGenerator idGenerator) {
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(passwordHash, "passwordHash no puede ser null");
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        String validatedFirstName = requireNonBlank(firstName, "El nombre es obligatorio");
        String validatedLastName = requireNonBlank(lastName, "El apellido es obligatorio");
        Email validatedEmail = Email.of(rawEmail);
        Set<Role> validatedRoles = validateRoles(roles);

        UUID id = idGenerator.newId();
        Instant now = clock.now();
        User user = new User(
                id,
                tenantId,
                validatedEmail,
                passwordHash,
                validatedFirstName,
                validatedLastName,
                UserStatus.ACTIVE,
                validatedRoles,
                now,
                now);
        Set<String> roleNames =
                validatedRoles.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
        user.domainEvents.add(
                new EmployeeCreated(idGenerator.newId(), now, tenantId, id, validatedEmail.value(), roleNames));
        return user;
    }

    /**
     * Reconstruye un User existente desde persistencia. No genera eventos de
     * dominio (no es un hecho nuevo).
     */
    public static User reconstitute(
            UUID id,
            UUID tenantId,
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            UserStatus status,
            Set<Role> roles,
            Instant createdAt,
            Instant updatedAt) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(status, "status no puede ser null");
        Objects.requireNonNull(createdAt, "createdAt no puede ser null");
        Objects.requireNonNull(updatedAt, "updatedAt no puede ser null");
        return new User(
                id,
                tenantId,
                Email.of(email),
                passwordHash,
                requireNonBlank(firstName, "El nombre es obligatorio"),
                requireNonBlank(lastName, "El apellido es obligatorio"),
                status,
                validateRoles(roles),
                createdAt,
                updatedAt);
    }

    public void activate(Clock clock) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        this.status = UserStatus.ACTIVE;
        this.updatedAt = clock.now();
    }

    public void deactivate(Clock clock, IdGenerator idGenerator) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        Instant now = clock.now();
        this.status = UserStatus.INACTIVE;
        this.updatedAt = now;
        domainEvents.add(new EmployeeDeactivated(idGenerator.newId(), now, tenantId, id));
    }

    public void assignRoles(Set<Role> roles, Clock clock) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        this.roles = validateRoles(roles);
        this.updatedAt = clock.now();
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /** Devuelve y limpia los eventos de dominio acumulados por el agregado. */
    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static Set<Role> validateRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("El usuario debe tener al menos un rol");
        }
        return EnumSet.copyOf(roles);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public Email email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public UserStatus status() {
        return status;
    }

    public Set<Role> roles() {
        return Set.copyOf(roles);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
