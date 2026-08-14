package com.tfp.timetracking.notification.domain;

/**
 * Hechos que se notifican al usuario (RF-NOT-003, T170).
 *
 * <p>El nombre viaja al frontend, que decide el texto y el icono: por eso es un
 * enum estable y no un texto libre. Anadir un valor obliga a anadir su
 * traduccion en la interfaz.
 *
 * <p>Los valores se agrupan por el rol al que se dirigen. El rol no forma parte
 * del enum: quien lo decide es la plantilla del consumidor, y el mismo hecho
 * puede generar tipos distintos para roles distintos —la anomalia de jornada
 * produce {@link #WORKDAY_ANOMALY_DETECTED} para el empleado y
 * {@link #TEAM_WORKDAY_ANOMALY} para su administrador—.
 */
public enum NotificationType {

    // Empleado.
    WORKDAY_ANOMALY_DETECTED,
    CORRECTION_APPROVED,
    CORRECTION_REJECTED,
    ABSENCE_APPROVED,
    ABSENCE_REJECTED,
    ACCOUNT_CREATED,
    ACCOUNT_DEACTIVATED,
    SHIFT_ASSIGNED,

    // Administrador de tenant.
    CORRECTION_REQUESTED,
    ABSENCE_REQUESTED,
    TEAM_WORKDAY_ANOMALY,
    TENANT_SUSPENDED,
    TENANT_REACTIVATED,
    TENANT_ARCHIVED,

    // Administrador de plataforma.
    REGISTRATION_PENDING_REVIEW,
    SYSTEM_QUEUE_STUCK
}
