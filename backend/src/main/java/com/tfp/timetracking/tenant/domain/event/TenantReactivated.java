package com.tfp.timetracking.tenant.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: un tenant suspendido ha sido reactivado (RF-TEN-007).
 * Hecho pasado e inmutable, sin dependencias de Spring ni JPA. Se genera
 * dentro de {@code Tenant.reactivate(...)}.
 *
 * @param eventId identificador único del evento
 * @param occurredAt instante en el que ocurrió el hecho (reloj de dominio)
 * @param tenantId identificador del tenant reactivado (coincide con {@code aggregateId})
 * @param aggregateId identificador del agregado {@link com.tfp.timetracking.tenant.domain.Tenant}
 */
public record TenantReactivated(UUID eventId, Instant occurredAt, UUID tenantId, UUID aggregateId) {}
