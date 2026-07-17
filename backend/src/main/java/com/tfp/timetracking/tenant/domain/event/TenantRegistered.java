package com.tfp.timetracking.tenant.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: un tenant ha sido registrado (CONTEXT-DOMINIO §3).
 *
 * <p>Hecho pasado e inmutable, sin dependencias de Spring ni JPA. Se genera
 * dentro de {@code Tenant.register(...)} y lo recoge el caso de uso tras
 * persistir el agregado (skill {@code create-domain-event}).
 *
 * @param eventId identificador unico del evento
 * @param occurredAt instante en el que ocurrio el hecho (reloj de dominio)
 * @param tenantId identificador del tenant registrado (coincide con
 *     {@code aggregateId})
 * @param aggregateId identificador del agregado {@link com.tfp.timetracking.tenant.domain.Tenant}
 * @param name nombre del tenant en el momento del registro
 * @param timezone zona horaria IANA del tenant en el momento del registro
 */
public record TenantRegistered(
        UUID eventId, Instant occurredAt, UUID tenantId, UUID aggregateId, String name, String timezone) {}
