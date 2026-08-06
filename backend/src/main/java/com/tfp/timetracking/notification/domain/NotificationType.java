package com.tfp.timetracking.notification.domain;

/**
 * Hechos que se notifican al usuario (RF-NOT-003).
 *
 * <p>El nombre viaja al frontend, que decide el texto y el icono: por eso es un
 * enum estable y no un texto libre. Anadir un valor obliga a anadir su
 * traduccion en la interfaz.
 */
public enum NotificationType {
    WORKDAY_ANOMALY_DETECTED,
    CORRECTION_APPROVED,
    CORRECTION_REJECTED,
    ABSENCE_APPROVED,
    ABSENCE_REJECTED
}
