package com.tfp.timetracking.tenant.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: un {@code PLATFORM_ADMIN} ha aprobado una solicitud de
 * alta (T53-03). El tenant resultante nace en estado {@code PENDING}: la
 * aprobación de la solicitud no equivale a activar el tenant.
 *
 * @param eventId identificador unico del evento
 * @param occurredAt instante en el que ocurrio el hecho (reloj de dominio)
 * @param registrationId identificador del agregado
 * @param tenantId tenant creado a partir de la solicitud
 * @param ownerUserId primer {@code TENANT_ADMIN} creado a partir de la solicitud
 */
public record TenantRegistrationApproved(
        UUID eventId, Instant occurredAt, UUID registrationId, UUID tenantId, UUID ownerUserId) {}
