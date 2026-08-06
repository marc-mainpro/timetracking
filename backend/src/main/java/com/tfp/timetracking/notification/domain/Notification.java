package com.tfp.timetracking.notification.domain;

import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Notificacion dirigida a un usuario (T110-02, RF-NOT-001, RF-NOT-006).
 *
 * <p>Cubre las dos caras del requisito con un unico agregado: el aviso que el
 * usuario ve dentro de la aplicacion —con su estado de leido— y su entrega por
 * correo, con estado y reintentos. Separarlos en dos agregados obligaria a
 * mantener sincronizados dos ciclos de vida del mismo hecho.
 *
 * <p>El destinatario se guarda <b>desnormalizado</b> ({@code recipientEmail}):
 * la notificacion es una foto del momento en que ocurrio el hecho. Si el usuario
 * cambia de correo despues, el aviso ya emitido no debe redirigirse, y el modulo
 * de notificacion no deberia tener que consultar a {@code identity} en cada
 * intento de envio.
 */
public final class Notification {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_BODY_LENGTH = 2000;
    private static final int MAX_ERROR_LENGTH = 500;

    private final UUID id;
    private final UUID tenantId;
    private final UUID recipientUserId;
    private final String recipientEmail;
    private final NotificationType type;
    private final String title;
    private final String body;
    private NotificationStatus status;
    private int attempts;
    private String lastError;
    private final Instant createdAt;
    private Instant sentAt;
    private Instant readAt;

    private Notification(
            UUID id,
            UUID tenantId,
            UUID recipientUserId,
            String recipientEmail,
            NotificationType type,
            String title,
            String body,
            NotificationStatus status,
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
        this.status = status;
        this.attempts = attempts;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
        this.readAt = readAt;
    }

    public static Notification create(
            UUID tenantId,
            UUID recipientUserId,
            String recipientEmail,
            NotificationType type,
            String title,
            String body,
            Instant now,
            IdGenerator idGenerator) {
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(recipientUserId, "recipientUserId no puede ser null");
        Objects.requireNonNull(type, "type no puede ser null");
        Objects.requireNonNull(now, "now no puede ser null");
        return new Notification(
                idGenerator.newId(),
                tenantId,
                recipientUserId,
                normalizeEmail(recipientEmail),
                type,
                requireText(title, "El titulo", MAX_TITLE_LENGTH),
                requireText(body, "El cuerpo", MAX_BODY_LENGTH),
                NotificationStatus.PENDING,
                0,
                null,
                now,
                null,
                null);
    }

    public static Notification reconstitute(
            UUID id,
            UUID tenantId,
            UUID recipientUserId,
            String recipientEmail,
            NotificationType type,
            String title,
            String body,
            NotificationStatus status,
            int attempts,
            String lastError,
            Instant createdAt,
            Instant sentAt,
            Instant readAt) {
        return new Notification(
                id,
                tenantId,
                recipientUserId,
                recipientEmail,
                type,
                title,
                body,
                status,
                attempts,
                lastError,
                createdAt,
                sentAt,
                readAt);
    }

    /** Registra un envio correcto. Solo desde {@code PENDING}. */
    public void markSent(Instant now) {
        requirePending("marcar como enviada");
        this.status = NotificationStatus.SENT;
        this.attempts++;
        this.lastError = null;
        this.sentAt = now;
    }

    /**
     * Registra un intento fallido. Si con este intento se alcanza
     * {@code maxAttempts}, la notificacion queda {@code FAILED} y no se
     * reintenta mas; en caso contrario sigue {@code PENDING}.
     *
     * <p>Una notificacion {@code FAILED} <b>sigue siendo visible</b> en la
     * aplicacion: que el correo no saliera no significa que el usuario no deba
     * enterarse del hecho.
     */
    public void markAttemptFailed(String error, int maxAttempts, Instant now) {
        requirePending("registrar un fallo de envio");
        this.attempts++;
        this.lastError = truncate(error);
        if (attempts >= maxAttempts) {
            this.status = NotificationStatus.FAILED;
            this.sentAt = null;
        }
        Objects.requireNonNull(now, "now no puede ser null");
    }

    /** Anula una notificacion pendiente. */
    public void cancel() {
        requirePending("cancelar");
        this.status = NotificationStatus.CANCELLED;
    }

    /** Marca como leida. Es idempotente: releer no cambia la fecha original. */
    public void markRead(Instant now) {
        if (readAt == null) {
            this.readAt = Objects.requireNonNull(now, "now no puede ser null");
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** Solo se envia por correo si hay direccion y sigue pendiente. */
    public boolean isDeliverable() {
        return status == NotificationStatus.PENDING && recipientEmail != null;
    }

    private void requirePending(String action) {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                    "No se puede " + action + " una notificacion en estado " + status);
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " no puede superar los " + maxLength + " caracteres");
        }
        return normalized;
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim();
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID recipientUserId() { return recipientUserId; }
    public String recipientEmail() { return recipientEmail; }
    public NotificationType type() { return type; }
    public String title() { return title; }
    public String body() { return body; }
    public NotificationStatus status() { return status; }
    public int attempts() { return attempts; }
    public String lastError() { return lastError; }
    public Instant createdAt() { return createdAt; }
    public Instant sentAt() { return sentAt; }
    public Instant readAt() { return readAt; }
}
