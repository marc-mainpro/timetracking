package com.tfp.timetracking.outbox.domain;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * Se lanza al intentar un reintento manual ({@code RetryFailedOutboxMessage},
 * T703) sobre un mensaje de outbox que no esta en estado {@link
 * OutboxMessageStatus#FAILED}. El reintento manual es una operacion humana
 * explicita (operacion/soporte), no parte del flujo automatico del
 * publicador; solo tiene sentido sobre mensajes que ya agotaron sus
 * reintentos automaticos.
 */
public final class OutboxMessageNotFailedException extends DomainException {

    public OutboxMessageNotFailedException(OutboxMessageStatus currentStatus) {
        super(
                "OUTBOX_MESSAGE_NOT_FAILED",
                "Solo se puede reintentar manualmente un mensaje de outbox en estado FAILED (actual: "
                        + currentStatus + ")");
    }
}
