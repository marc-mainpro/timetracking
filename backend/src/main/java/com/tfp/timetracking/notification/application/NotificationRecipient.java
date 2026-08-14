package com.tfp.timetracking.notification.application;

import java.util.UUID;

/**
 * Destinatario resuelto de una notificacion (T170-01).
 *
 * <p>Lleva el correo junto al identificador porque {@code notification} guarda
 * la direccion desnormalizada en el agregado: resolver rol y correo en la misma
 * consulta evita una segunda pregunta a {@code identity} por cada destinatario
 * del fan-out.
 *
 * @param userId usuario al que va dirigida la notificacion
 * @param email direccion en el momento del hecho, o {@code null} si no consta
 */
public record NotificationRecipient(UUID userId, String email) {}
