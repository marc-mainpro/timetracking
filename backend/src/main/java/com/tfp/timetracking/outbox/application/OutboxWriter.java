package com.tfp.timetracking.outbox.application;

import java.util.Map;
import java.util.UUID;

/**
 * Unico puerto que el resto de modulos usa para escribir eventos de
 * integracion en el Transactional Outbox (ADR-0005). La escritura debe
 * ocurrir en la misma transaccion que el cambio de negocio que origina el
 * evento (SDD §14.1); por eso este puerto no publica nada por si mismo, solo
 * persiste el mensaje.
 *
 * <p>T701 solo entrega infraestructura + este contrato. T702 lo conectara
 * desde las transacciones de negocio.
 */
public interface OutboxWriter {

    /**
     * Escribe un mensaje de outbox para el evento de integracion indicado.
     *
     * @param aggregateType tipo del agregado que origina el evento (p.ej. "Workday")
     * @param aggregateId identificador del agregado
     * @param eventType nombre versionado del evento del catalogo (p.ej. "time-tracking.workday-closed.v1")
     * @param eventVersion version del esquema del payload
     * @param payload cuerpo del evento, ya serializable, sin entidades JPA ni modelos internos
     */
    void write(String aggregateType, UUID aggregateId, String eventType, int eventVersion, Map<String, Object> payload);
}
