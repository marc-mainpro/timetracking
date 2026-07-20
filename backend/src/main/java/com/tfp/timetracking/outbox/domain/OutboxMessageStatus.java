package com.tfp.timetracking.outbox.domain;

/**
 * Estados del ciclo de vida de un mensaje de la tabla {@code outbox_message}
 * (SDD §14.2).
 */
public enum OutboxMessageStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}
