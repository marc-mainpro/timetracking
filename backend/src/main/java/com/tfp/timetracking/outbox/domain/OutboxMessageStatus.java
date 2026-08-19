package com.tfp.timetracking.outbox.domain;

/**
 * Estados del ciclo de vida de un mensaje de la tabla {@code outbox_message}
 * (SDD §14.2).
 */
public enum OutboxMessageStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED,
    /**
     * Descartado manualmente desde el panel de plataforma: alguien decidio que
     * este evento ya no se va a publicar. La fila se conserva con su {@code
     * lastError} como traza; el motivo y el actor quedan en auditoria.
     */
    DISCARDED
}
