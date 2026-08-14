package com.tfp.timetracking.tenant.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: el solicitante ha demostrado control del correo indicado
 * y la solicitud pasa a revisión de plataforma (RF-REG-004).
 *
 * @param eventId identificador unico del evento
 * @param occurredAt instante en el que ocurrio el hecho (reloj de dominio)
 * @param registrationId identificador del agregado
 * @param email correo verificado, ya normalizado
 * @param companyName nombre de la organización solicitante; lo necesita el
 *     aviso al administrador de plataforma para poder redactar un cuerpo útil
 *     sin volver a consultar la solicitud (T170-07)
 */
public record TenantRegistrationEmailVerified(
        UUID eventId, Instant occurredAt, UUID registrationId, String email, String companyName) {}
