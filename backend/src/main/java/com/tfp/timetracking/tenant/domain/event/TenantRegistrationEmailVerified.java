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
 */
public record TenantRegistrationEmailVerified(
        UUID eventId, Instant occurredAt, UUID registrationId, String email) {}
