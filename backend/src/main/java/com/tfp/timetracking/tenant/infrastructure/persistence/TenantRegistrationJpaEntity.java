package com.tfp.timetracking.tenant.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA de {@code tenant_registration} (migración V12). Separada del
 * agregado {@link com.tfp.timetracking.tenant.domain.TenantRegistration}: ni
 * una anotación de esta clase se filtra al dominio.
 */
@Entity
@Table(name = "tenant_registration")
public class TenantRegistrationJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "owner_first_name", nullable = false, length = 200)
    private String ownerFirstName;

    @Column(name = "owner_last_name", nullable = false, length = 200)
    private String ownerLastName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "owner_password_hash", nullable = false, length = 255)
    private String ownerPasswordHash;

    @Column(name = "timezone", nullable = false, length = 60)
    private String timezone;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "verification_token_hash", length = 64)
    private String verificationTokenHash;

    @Column(name = "verification_token_expires_at")
    private Instant verificationTokenExpiresAt;

    @Column(name = "verification_sent_at")
    private Instant verificationSentAt;

    @Column(name = "resend_count", nullable = false)
    private int resendCount;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "created_tenant_id")
    private UUID createdTenantId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected TenantRegistrationJpaEntity() {
        // Requerido por JPA.
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public TenantRegistrationJpaEntity(
            UUID id,
            String companyName,
            String ownerFirstName,
            String ownerLastName,
            String email,
            String ownerPasswordHash,
            String timezone,
            String status,
            String verificationTokenHash,
            Instant verificationTokenExpiresAt,
            Instant verificationSentAt,
            int resendCount,
            String source,
            String ipHash,
            String decisionReason,
            UUID createdTenantId,
            Instant createdAt,
            Instant updatedAt,
            Instant verifiedAt,
            Instant decidedAt) {
        this.id = id;
        this.companyName = companyName;
        this.ownerFirstName = ownerFirstName;
        this.ownerLastName = ownerLastName;
        this.email = email;
        this.ownerPasswordHash = ownerPasswordHash;
        this.timezone = timezone;
        this.status = status;
        this.verificationTokenHash = verificationTokenHash;
        this.verificationTokenExpiresAt = verificationTokenExpiresAt;
        this.verificationSentAt = verificationSentAt;
        this.resendCount = resendCount;
        this.source = source;
        this.ipHash = ipHash;
        this.decisionReason = decisionReason;
        this.createdTenantId = createdTenantId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.verifiedAt = verifiedAt;
        this.decidedAt = decidedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getOwnerFirstName() {
        return ownerFirstName;
    }

    public String getOwnerLastName() {
        return ownerLastName;
    }

    public String getEmail() {
        return email;
    }

    public String getOwnerPasswordHash() {
        return ownerPasswordHash;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getStatus() {
        return status;
    }

    public String getVerificationTokenHash() {
        return verificationTokenHash;
    }

    public Instant getVerificationTokenExpiresAt() {
        return verificationTokenExpiresAt;
    }

    public Instant getVerificationSentAt() {
        return verificationSentAt;
    }

    public int getResendCount() {
        return resendCount;
    }

    public String getSource() {
        return source;
    }

    public String getIpHash() {
        return ipHash;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public UUID getCreatedTenantId() {
        return createdTenantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
