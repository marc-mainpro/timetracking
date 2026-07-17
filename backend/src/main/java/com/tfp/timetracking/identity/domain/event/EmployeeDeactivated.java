package com.tfp.timetracking.identity.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: un usuario/empleado ha sido desactivado
 * (CONTEXT-DOMINIO §3). Hecho pasado e inmutable, sin Spring ni JPA.
 * Generado en {@code User.deactivate()}.
 *
 * @param eventId identificador unico del evento
 * @param occurredAt instante del hecho (reloj de dominio)
 * @param tenantId tenant al que pertenece el usuario desactivado
 * @param aggregateId identificador del agregado {@link com.tfp.timetracking.identity.domain.User}
 */
public record EmployeeDeactivated(UUID eventId, Instant occurredAt, UUID tenantId, UUID aggregateId) {}
