package com.tfp.timetracking.tenant.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: un tenant activo ha sido suspendido (RF-TEN-006). Hecho
 * pasado e inmutable, sin dependencias de Spring ni JPA. Se genera dentro de
 * {@code Tenant.suspend(...)}.
 *
 * @param eventId identificador único del evento
 * @param occurredAt instante en el que ocurrió el hecho (reloj de dominio)
 * @param tenantId identificador del tenant suspendido (coincide con {@code aggregateId})
 * @param aggregateId identificador del agregado {@link com.tfp.timetracking.tenant.domain.Tenant}
 * @param reason motivo de la suspensión
 */
public record TenantSuspended(UUID eventId, Instant occurredAt, UUID tenantId, UUID aggregateId, String reason) {}
