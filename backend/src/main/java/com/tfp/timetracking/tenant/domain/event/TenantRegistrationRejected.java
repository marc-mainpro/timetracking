package com.tfp.timetracking.tenant.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: un {@code PLATFORM_ADMIN} ha rechazado una solicitud de
 * alta, con motivo obligatorio (T53-03, diseño §7.5).
 *
 * @param eventId identificador unico del evento
 * @param occurredAt instante en el que ocurrio el hecho (reloj de dominio)
 * @param registrationId identificador del agregado
 * @param reason motivo del rechazo
 */
public record TenantRegistrationRejected(
        UUID eventId, Instant occurredAt, UUID registrationId, String reason) {}
