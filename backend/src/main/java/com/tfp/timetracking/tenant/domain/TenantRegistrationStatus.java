package com.tfp.timetracking.tenant.domain;

/**
 * Estados de una {@link TenantRegistration} (T53-01, diseño §7.3).
 *
 * <p>Ciclo de vida:
 *
 * <pre>
 * PENDING_EMAIL_VERIFICATION → PENDING_REVIEW → APPROVED → CONSUMED
 *                            ↘ EXPIRED       ↘ REJECTED
 * </pre>
 *
 * <p>{@code CONSUMED} es el estado terminal del camino feliz: la solicitud ya
 * dio lugar a un {@code Tenant} en estado {@code PENDING} y a su primer
 * {@code TENANT_ADMIN}; no puede volver a consumirse (T53-03: idempotencia).
 */
public enum TenantRegistrationStatus {

    /** Solicitud creada; falta que el solicitante demuestre control del correo (RF-REG-004). */
    PENDING_EMAIL_VERIFICATION,

    /** Correo verificado; espera la decisión de un {@code PLATFORM_ADMIN}. */
    PENDING_REVIEW,

    /** Aprobada por plataforma; pendiente de materializarse en un tenant. */
    APPROVED,

    /** Rechazada por plataforma, con motivo obligatorio. */
    REJECTED,

    /** El token de verificación caducó sin usarse. */
    EXPIRED,

    /** Ya se creó el tenant y su propietario a partir de esta solicitud. */
    CONSUMED;

    /** Estados en los que la solicitud sigue viva y bloquea una nueva solicitud del mismo correo. */
    public boolean isOpen() {
        return this == PENDING_EMAIL_VERIFICATION || this == PENDING_REVIEW || this == APPROVED;
    }
}
