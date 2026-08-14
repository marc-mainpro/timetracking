package com.tfp.timetracking.notification.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Fila de {@code notification} (T110-02). */
@Entity
@Table(name = "notification")
public class NotificationJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Column(name = "email_required", nullable = false)
    private boolean emailRequired;

    @Column(name = "action_path", length = 200)
    private String actionPath;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    /** Requerido por JPA. */
    protected NotificationJpaEntity() {}

    public NotificationJpaEntity(
            UUID id,
            UUID tenantId,
            UUID recipientUserId,
            String recipientEmail,
            String type,
            String title,
            String body,
            boolean emailRequired,
            String actionPath,
            String status,
            int attempts,
            String lastError,
            Instant createdAt,
            Instant sentAt,
            Instant readAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.recipientUserId = recipientUserId;
        this.recipientEmail = recipientEmail;
        this.type = type;
        this.title = title;
        this.body = body;
        this.emailRequired = emailRequired;
        this.actionPath = actionPath;
        this.status = status;
        this.attempts = attempts;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
        this.readAt = readAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public boolean isEmailRequired() { return emailRequired; }
    public String getActionPath() { return actionPath; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getReadAt() { return readAt; }
}
