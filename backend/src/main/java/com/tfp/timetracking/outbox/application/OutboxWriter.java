package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.shared.domain.IntegrationEvent;

/**
 * Unico puerto que el resto de modulos usa para escribir eventos de
 * integracion en el Transactional Outbox (ADR-0005). La escritura debe
 * ocurrir en la misma transaccion que el cambio de negocio que origina el
 * evento (SDD §14.1); por eso este puerto no publica nada por si mismo, solo
 * persiste el mensaje.
 *
 * <p>T701 entrego infraestructura + un contrato provisional con parametros
 * sueltos ({@code aggregateType/aggregateId/eventType/eventVersion/payload}).
 * T702 lo sustituye por un unico parametro {@link IntegrationEvent}: los
 * mappers {@code DomainEvent -> IntegrationEvent} de cada modulo ya calculan
 * {@code eventId} (reutilizando el del evento de dominio, para trazabilidad
 * end-to-end) y {@code occurredAt} (instante real del hecho de negocio); con
 * la firma anterior esos dos valores se habrian descartado y la
 * implementacion los habria vuelto a generar en el momento de escribir en el
 * outbox, perdiendo esa informacion. El {@code tenantId} del envelope se
 * persiste tal cual (viene del evento de dominio de origen, no de un valor
 * de request sin validar): no se resuelve desde {@code TenantContext} porque
 * algunas acciones que publican eventos son deliberadamente anonimas (p.ej.
 * el registro de un tenant nuevo, que ocurre en un endpoint publico sin JWT
 * todavia).
 */
public interface OutboxWriter {

    /**
     * Escribe un mensaje de outbox para el evento de integracion indicado.
     *
     * @param event envelope ya mapeado desde el evento de dominio de origen,
     *     sin entidades JPA ni modelos internos en su payload
     */
    void write(IntegrationEvent event);
}
