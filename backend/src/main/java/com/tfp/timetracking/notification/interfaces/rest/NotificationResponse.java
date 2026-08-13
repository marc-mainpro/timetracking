package com.tfp.timetracking.notification.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

/**
 * Notificacion tal como la ve el usuario. No expone el correo ni el ultimo
 * error.
 *
 * <p>{@code actionPath} es la ruta del frontend a la que lleva el aviso, o
 * {@code null} si es puramente informativo (T170-02).
 */
public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        String actionPath,
        Instant createdAt,
        Instant readAt,
        boolean read) {}
