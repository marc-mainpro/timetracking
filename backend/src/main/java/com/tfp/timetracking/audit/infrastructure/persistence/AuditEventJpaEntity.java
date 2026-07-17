package com.tfp.timetracking.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "audit_event")
public class AuditEventJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEventJpaEntity() {}

    public AuditEventJpaEntity(
            UUID id,
            UUID tenantId,
            UUID actorUserId,
            String action,
            String entityType,
            UUID entityId,
            UUID correlationId,
            String metadata,
            Instant occurredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.correlationId = correlationId;
        this.metadata = metadata;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public UUID getCorrelationId() { return correlationId; }
    public String getMetadata() { return metadata; }
    public Instant getOccurredAt() { return occurredAt; }
}
