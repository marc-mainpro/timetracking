package com.tfp.timetracking.notification.domain;

/** Estado de entrega de una notificacion (RF-NOT-006). */
public enum NotificationStatus {
    /** Creada, pendiente de enviar por correo. */
    PENDING,
    /** Enviada por correo correctamente. */
    SENT,
    /** Agotados los reintentos de envio. Sigue visible en la aplicacion. */
    FAILED,
    /** Anulada antes de enviarse: el hecho que la motivo dejo de ser relevante. */
    CANCELLED
}
