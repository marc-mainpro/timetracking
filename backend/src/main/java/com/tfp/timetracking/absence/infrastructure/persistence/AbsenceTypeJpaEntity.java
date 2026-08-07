package com.tfp.timetracking.absence.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "absence_type")
public class AbsenceTypeJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "allows_attachment", nullable = false)
    private boolean allowsAttachment;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected AbsenceTypeJpaEntity() {}

    public AbsenceTypeJpaEntity(
            UUID id,
            UUID tenantId,
            String code,
            String name,
            boolean requiresApproval,
            boolean allowsAttachment,
            boolean active) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.requiresApproval = requiresApproval;
        this.allowsAttachment = allowsAttachment;
        this.active = active;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public boolean isAllowsAttachment() { return allowsAttachment; }
    public boolean isActive() { return active; }
}
