package com.tfp.timetracking.corrections.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "correction_request")
public class CorrectionRequestJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workday_id", nullable = false)
    private UUID workdayId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "proposed_changes", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String proposedChanges;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_comment")
    private String resolutionComment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CorrectionRequestJpaEntity() {}

    public CorrectionRequestJpaEntity(
            UUID id,
            UUID tenantId,
            UUID workdayId,
            UUID requestedBy,
            String reason,
            String proposedChanges,
            String status,
            UUID resolvedBy,
            Instant resolvedAt,
            String resolutionComment,
            Instant createdAt,
            long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.workdayId = workdayId;
        this.requestedBy = requestedBy;
        this.reason = reason;
        this.proposedChanges = proposedChanges;
        this.status = status;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
        this.resolutionComment = resolutionComment;
        this.createdAt = createdAt;
        this.version = version;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getWorkdayId() { return workdayId; }
    public UUID getRequestedBy() { return requestedBy; }
    public String getReason() { return reason; }
    public String getProposedChanges() { return proposedChanges; }
    public String getStatus() { return status; }
    public UUID getResolvedBy() { return resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolutionComment() { return resolutionComment; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
