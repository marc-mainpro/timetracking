package com.tfp.timetracking.identity.domain.event;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Evento de dominio: un usuario/empleado ha sido creado (CONTEXT-DOMINIO §3).
 * Hecho pasado e inmutable, sin Spring ni JPA. Generado en
 * {@code User.create(...)}.
 *
 * @param eventId identificador unico del evento
 * @param occurredAt instante del hecho (reloj de dominio)
 * @param tenantId tenant al que pertenece el usuario creado
 * @param aggregateId identificador del agregado {@link com.tfp.timetracking.identity.domain.User}
 * @param email email normalizado del usuario en el momento de la creacion
 * @param roles roles asignados en el momento de la creacion
 */
public record EmployeeCreated(
        UUID eventId, Instant occurredAt, UUID tenantId, UUID aggregateId, String email, Set<String> roles) {

    public EmployeeCreated {
        roles = Set.copyOf(roles);
    }
}
