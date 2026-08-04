package com.tfp.timetracking.calendar.domain.model;

import com.tfp.timetracking.calendar.domain.event.CalendarAssigned;
import com.tfp.timetracking.calendar.domain.event.CalendarAssignmentRemoved;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado <b>CalendarAssignment</b> (T70-02, RF-CAL-006): vincula un
 * {@link WorkCalendar} a un ambito —todo el tenant, un equipo o un empleado—.
 *
 * <p>Es un agregado propio y no una entidad interna de {@code WorkCalendar}
 * porque su ciclo de vida es independiente: se asigna y se retira sin editar el
 * calendario, y un mismo calendario puede tener muchas asignaciones.
 *
 * <p><b>Sin vigencia propia.</b> La vigencia temporal vive en el calendario
 * (RF-CAL-005): programar un cambio de horario se hace creando un calendario con
 * la nueva vigencia, no versionando la asignacion. Esto mantiene la resolucion
 * con una unica dimension temporal y evita solapes ambiguos entre dos periodos
 * de validez.
 *
 * <p><b>Equipos.</b> {@code targetId} de ambito {@code TEAM} es un identificador
 * opaco: el sistema todavia no tiene gestion de equipos (ADR-0013). Quien
 * resuelve el calendario efectivo aporta el equipo del empleado; este modulo no
 * lo consulta ni lo valida contra ninguna tabla de pertenencia.
 */
public final class CalendarAssignment {

    private final UUID id;
    private final UUID tenantId;
    private final UUID calendarId;
    private final AssignmentScope scope;
    private final UUID targetId;
    private final Instant createdAt;
    private Instant updatedAt;

    private final List<Object> domainEvents = new ArrayList<>();

    private CalendarAssignment(
            UUID id,
            UUID tenantId,
            UUID calendarId,
            AssignmentScope scope,
            UUID targetId,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.calendarId = calendarId;
        this.scope = scope;
        this.targetId = targetId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factoria: asigna un calendario a un ambito y emite {@link CalendarAssigned}.
     *
     * @param targetId equipo o empleado destinatario; debe ser {@code null} en
     *     ambito {@code TENANT} y no nulo en {@code TEAM}/{@code EMPLOYEE}
     */
    public static CalendarAssignment create(
            UUID tenantId,
            UUID calendarId,
            AssignmentScope scope,
            UUID targetId,
            Clock clock,
            IdGenerator idGenerator) {
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(calendarId, "calendarId no puede ser null");
        Objects.requireNonNull(scope, "scope no puede ser null");
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        validateTarget(scope, targetId);

        UUID assignmentId = idGenerator.newId();
        Instant now = clock.now();
        CalendarAssignment assignment =
                new CalendarAssignment(assignmentId, tenantId, calendarId, scope, targetId, now, now);
        assignment.domainEvents.add(
                new CalendarAssigned(idGenerator.newId(), now, tenantId, assignmentId, calendarId, scope, targetId));
        return assignment;
    }

    /** Reconstruye una asignacion desde persistencia. No genera eventos de dominio. */
    public static CalendarAssignment reconstitute(
            UUID id,
            UUID tenantId,
            UUID calendarId,
            AssignmentScope scope,
            UUID targetId,
            Instant createdAt,
            Instant updatedAt) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(calendarId, "calendarId no puede ser null");
        Objects.requireNonNull(scope, "scope no puede ser null");
        Objects.requireNonNull(createdAt, "createdAt no puede ser null");
        Objects.requireNonNull(updatedAt, "updatedAt no puede ser null");
        validateTarget(scope, targetId);
        return new CalendarAssignment(id, tenantId, calendarId, scope, targetId, createdAt, updatedAt);
    }

    /**
     * Marca la retirada de la asignacion emitiendo
     * {@link CalendarAssignmentRemoved}. El borrado fisico lo hace el repositorio;
     * el agregado solo produce el hecho de negocio, que el caso de uso publica
     * tras persistir.
     */
    public void remove(Clock clock, IdGenerator idGenerator) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        Instant now = clock.now();
        this.updatedAt = now;
        this.domainEvents.add(
                new CalendarAssignmentRemoved(idGenerator.newId(), now, tenantId, id, calendarId, scope, targetId));
    }

    /**
     * {@code true} si esta asignacion es candidata para el empleado indicado.
     *
     * <p>Regla: {@code TENANT} aplica siempre; {@code TEAM} aplica si el equipo
     * aportado por el llamante coincide con el destinatario; {@code EMPLOYEE}
     * aplica si el empleado coincide. Un {@code teamId} nulo (empleado sin
     * equipo conocido) descarta las asignaciones de equipo, nunca las hace
     * coincidir.
     */
    public boolean appliesTo(UUID employeeId, UUID teamId) {
        return switch (scope) {
            case TENANT -> true;
            case TEAM -> teamId != null && teamId.equals(targetId);
            case EMPLOYEE -> employeeId != null && employeeId.equals(targetId);
        };
    }

    /** Devuelve y limpia los eventos de dominio acumulados por el agregado. */
    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private static void validateTarget(AssignmentScope scope, UUID targetId) {
        if (scope.requiresTarget() && targetId == null) {
            throw new IllegalArgumentException("El ambito " + scope + " exige un destinatario");
        }
        if (!scope.requiresTarget() && targetId != null) {
            throw new IllegalArgumentException("El ambito " + scope + " no admite destinatario");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID calendarId() {
        return calendarId;
    }

    public AssignmentScope scope() {
        return scope;
    }

    public UUID targetId() {
        return targetId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
