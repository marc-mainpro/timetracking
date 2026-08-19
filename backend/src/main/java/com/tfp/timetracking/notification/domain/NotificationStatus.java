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
    CANCELLED,
    /**
     * Envio por correo abandonado manualmente desde el panel de plataforma tras
     * agotar los reintentos. No es {@link #CANCELLED}: el hecho sigue siendo
     * relevante, lo que se abandona es el intento de entrega.
     *
     * <p>Como toda notificacion {@code FAILED}, <b>sigue visible en la
     * aplicacion</b>: renunciar al correo no significa ocultar el aviso.
     */
    DISCARDED
}
