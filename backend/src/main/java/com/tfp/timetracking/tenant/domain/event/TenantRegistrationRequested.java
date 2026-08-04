package com.tfp.timetracking.tenant.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: se ha recibido una solicitud de alta pública de tenant
 * (T53-01/T53-03, RF-REG-001).
 *
 * <p>Hecho pasado e inmutable. <b>No contiene el token de verificación</b>: es
 * el evento «público» de la solicitud, y su equivalente de integración se
 * persiste en el outbox y puede leerlo cualquier consumidor. El token viaja en
 * {@link TenantRegistrationVerificationRequested}.
 *
 * @param eventId identificador unico del evento
 * @param occurredAt instante en el que ocurrio el hecho (reloj de dominio)
 * @param registrationId identificador del agregado
 *     {@link com.tfp.timetracking.tenant.domain.TenantRegistration}
 * @param companyName nombre de la organización solicitada
 * @param email correo del propietario, ya normalizado
 * @param source canal por el que entró la solicitud (p. ej. {@code PUBLIC_WEB})
 */
public record TenantRegistrationRequested(
        UUID eventId, Instant occurredAt, UUID registrationId, String companyName, String email, String source) {}
