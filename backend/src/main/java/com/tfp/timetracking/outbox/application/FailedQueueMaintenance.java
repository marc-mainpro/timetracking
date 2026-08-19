package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.shared.domain.PagedResult;
import java.time.Instant;
import java.util.UUID;

/**
 * Punto de contribucion para el mantenimiento manual de una cola con
 * reintentos (ADR-0011), hermano de {@link QueueStatusContributor}.
 *
 * <p>El panel tecnico no solo dice cuantos elementos agotaron sus reintentos:
 * tambien permite verlos, reintentarlos y descartarlos. Cada cola aporta aqui
 * como se hace en su modulo, de modo que un unico controlador de plataforma
 * sirve todas las colas sin que {@code outbox} tenga que conocer a
 * {@code notification} —que ya depende de el, y cerraria un ciclo—.
 *
 * <p>Se mantiene <b>separada</b> de {@link QueueStatusContributor} a proposito:
 * una cola puede querer informar de su estado sin admitir intervencion manual.
 * La correspondencia entre ambas por {@code name}/{@code queueName} la vigila
 * un test, porque de otro modo seria una convencion de cadenas que se rompe en
 * silencio y deja una cola con fallos y sin acciones posibles.
 */
public interface FailedQueueMaintenance {

    /**
     * Un elemento fallido, en los terminos comunes a todas las colas.
     *
     * <p>Deliberadamente <b>no</b> incluye el contenido del elemento (payload
     * del evento, cuerpo o destinatario de la notificacion): quien mira este
     * panel necesita saber que fallo y por que, no leer datos personales de
     * todos los tenants.
     *
     * @param type que es el elemento: tipo de evento o de notificacion
     * @param reference a que se refiere, para poder rastrearlo en su modulo
     * @param lastError ultimo error registrado; puede ser una traza larga
     */
    record FailedQueueEntry(
            UUID id,
            UUID tenantId,
            String type,
            String reference,
            int attempts,
            String lastError,
            Instant occurredAt) {}

    /** Identificador de la cola, el mismo que publica {@link QueueStatusContributor}. */
    String queueName();

    /** Elementos fallidos, del mas antiguo al mas reciente. */
    PagedResult<FailedQueueEntry> listFailed(int page, int size);

    /**
     * Devuelve el elemento a la cola.
     *
     * @return la foto del elemento <b>antes</b> de reintentarlo, para que quien
     *     orquesta pueda auditar la accion sin volver a consultarlo
     */
    FailedQueueEntry retry(UUID id);

    /**
     * Abandona el elemento conservando su traza.
     *
     * @return la foto del elemento antes de descartarlo
     */
    FailedQueueEntry discard(UUID id);
}
