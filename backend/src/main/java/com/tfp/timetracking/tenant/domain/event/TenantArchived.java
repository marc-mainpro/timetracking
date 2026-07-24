package com.tfp.timetracking.tenant.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: un tenant ha sido archivado de forma permanente
 * (RF-TEN-008). Hecho pasado e inmutable, sin dependencias de Spring ni JPA.
 * Se genera dentro de {@code Tenant.archive(...)}.
 *
 * @param eventId identificador único del evento
 * @param occurredAt instante en el que ocurrió el hecho (reloj de dominio)
 * @param tenantId identificador del tenant archivado (coincide con {@code aggregateId})
 * @param aggregateId identificador del agregado {@link com.tfp.timetracking.tenant.domain.Tenant}
 * @param reason motivo del archivado, puede ser {@code null}
 */
public record TenantArchived(UUID eventId, Instant occurredAt, UUID tenantId, UUID aggregateId, String reason) {}
