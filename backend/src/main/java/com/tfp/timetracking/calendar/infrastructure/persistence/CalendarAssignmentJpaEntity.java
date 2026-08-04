package com.tfp.timetracking.calendar.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA de {@code calendar_assignment} (migracion V16).
 *
 * <p>{@code scopeTargetId} es un {@link UUID} opaco sin clave ajena: en ambito
 * {@code TEAM} referencia un equipo que el sistema todavia no gestiona
 * (ADR-0013), y en {@code EMPLOYEE} un usuario de otro modulo, que no se
 * referencia por FK para no acoplar el esquema de {@code calendar} al de
 * {@code identity}.
 */
@Entity
@Table(name = "calendar_assignment")
public class CalendarAssignmentJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "calendar_id", nullable = false)
    private UUID calendarId;

    @Column(name = "scope", nullable = false, length = 20)
    private String scope;

    @Column(name = "scope_target_id")
    private UUID scopeTargetId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CalendarAssignmentJpaEntity() {
        // Requerido por JPA.
    }

    public CalendarAssignmentJpaEntity(
            UUID id,
            UUID tenantId,
            UUID calendarId,
            String scope,
            UUID scopeTargetId,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.calendarId = calendarId;
        this.scope = scope;
        this.scopeTargetId = scopeTargetId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCalendarId() {
        return calendarId;
    }

    public String getScope() {
        return scope;
    }

    public UUID getScopeTargetId() {
        return scopeTargetId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
