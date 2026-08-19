package com.tfp.timetracking.notification.domain;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * Se lanza al reintentar o descartar manualmente una notificacion que no esta
 * en estado {@link NotificationStatus#FAILED}.
 *
 * <p>Ambas son operaciones humanas de plataforma sobre un envio que ya agoto
 * sus reintentos automaticos; sobre cualquier otro estado no tienen sentido.
 * Simetrica a {@code OutboxMessageNotFailedException} en el modulo outbox.
 */
public final class NotificationNotFailedException extends DomainException {

    public NotificationNotFailedException(String action, NotificationStatus currentStatus) {
        super(
                "NOTIFICATION_NOT_FAILED",
                "Solo se puede " + action + " una notificacion en estado FAILED (actual: " + currentStatus + ")");
    }
}
