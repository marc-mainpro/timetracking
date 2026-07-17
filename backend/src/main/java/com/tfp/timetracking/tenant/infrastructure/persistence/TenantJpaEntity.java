package com.tfp.timetracking.tenant.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA de persistencia para {@code tenant} (tabla creada en
 * V2__identity.sql). Separada del agregado de dominio
 * {@link com.tfp.timetracking.tenant.domain.Tenant} (CONTEXT-GLOBAL §4):
 * ningun campo ni anotacion de esta clase se filtra al dominio.
 */
@Entity
@Table(name = "tenant")
public class TenantJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "timezone", nullable = false, length = 60)
    private String timezone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TenantJpaEntity() {
        // Requerido por JPA.
    }

    public TenantJpaEntity(UUID id, String name, String status, String timezone, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.timezone = timezone;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
