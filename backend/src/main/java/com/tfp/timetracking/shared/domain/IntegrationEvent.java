package com.tfp.timetracking.shared.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope de un evento de integracion (CONTEXT-DOMINIO §4, ADR-0005), listo
 * para escribirse en el Transactional Outbox. Es el resultado de traducir un
 * evento de dominio (interno, por agregado) a un contrato externo versionado
 * y estable (T702).
 *
 * <p>Vive en {@code shared.domain}, junto a {@link DomainEventPublisher} (el
 * puerto que recibe los eventos de dominio antes de mapearlos a este
 * envelope), y no en el modulo {@code outbox}: los mappers {@code
 * DomainEvent -> IntegrationEvent} de cada modulo de negocio
 * ({@code identity}, {@code tenant}, {@code timetracking}, {@code
 * corrections}) necesitan construir este tipo, y el modulo {@code outbox}
 * necesita leerlo desde esos mismos mappers al implementar
 * {@link DomainEventPublisher}; si viviera en {@code outbox.domain} se
 * formaria un ciclo de dependencia entre modulos ({@code outbox ->
 * <modulo> -> outbox}), prohibido por {@code ModuleCyclesTest}
 * (CONTEXT-GLOBAL §4 / T106 regla 4). Al vivir en {@code shared}, todos los
 * modulos pueden depender de el sin ciclos: {@code shared} no depende de
 * ningun modulo de negocio ni de {@code outbox}.
 *
 * <p>Nunca contiene entidades JPA ni agregados de dominio: {@code payload} es
 * un mapa serializable con los datos minimos (ids, instantes, estado) que
 * necesita un consumidor externo. Ver {@code docs/integration/event-catalog.md}
 * para el catalogo de tipos y sus payloads.
 *
 * @param eventId identificador unico del evento de integracion; coincide con
 *     el {@code eventId} del evento de dominio de origen para permitir
 *     trazabilidad/idempotencia en consumidores externos
 * @param eventType nombre versionado del catalogo (p.ej.
 *     {@code "time-tracking.workday-closed.v1"}), convencion
 *     {@code dominio.hecho.vN}
 * @param eventVersion version del esquema del payload (parte numerica del
 *     nombre en {@code eventType}, duplicada aqui para facilitar el uso
 *     programatico)
 * @param occurredAt instante en el que ocurrio el hecho de negocio (reloj de
 *     dominio en el momento en que se genero el evento de dominio), no el
 *     instante en el que se escribe en el outbox
 * @param tenantId tenant al que pertenece el evento; se toma del evento de
 *     dominio de origen (no de {@code TenantContext}), porque algunas
 *     acciones que publican eventos son deliberadamente anonimas (p.ej. el
 *     registro de un tenant nuevo, en un endpoint publico sin JWT todavia)
 * @param aggregateId identificador del agregado de dominio que origino el
 *     evento
 * @param aggregateType tipo del agregado de origen (p.ej. {@code "Workday"});
 *     no forma parte del envelope publico de CONTEXT-DOMINIO §4 pero es
 *     necesario para poblar la columna {@code aggregate_type} del outbox
 *     (T702: extension deliberada, ver report)
 * @param payload cuerpo minimo del evento, ya serializable (ids, instantes,
 *     cadenas, colecciones simples), nunca entidades JPA ni agregados
 */
public record IntegrationEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID tenantId,
        UUID aggregateId,
        String aggregateType,
        Map<String, Object> payload) {

    public IntegrationEvent {
        payload = Map.copyOf(payload);
    }
}
