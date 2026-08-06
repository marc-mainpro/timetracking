package com.tfp.timetracking.notification.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

/** Notificacion tal como la ve el usuario. No expone el correo ni el ultimo error. */
public record NotificationResponse(
        UUID id, String type, String title, String body, Instant createdAt, Instant readAt, boolean read) {}
