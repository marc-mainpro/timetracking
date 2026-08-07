package com.tfp.timetracking.tenant.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: hay que hacer llegar al solicitante un token de
 * verificación de correo (T53-05, RF-REG-004). Se emite tanto en el alta
 * inicial como en cada reenvío.
 *
 * <p>Es el <b>único</b> portador del token en claro. Existe precisamente para
 * que ese valor viaje por un evento distinto del que describe el hecho de
 * negocio ({@link TenantRegistrationRequested}), de modo que quien solo
 * necesite saber «se ha solicitado un alta» no tenga que ver el secreto.
 *
 * <p>El envío del correo ocurre fuera de la transacción de negocio, en el
 * consumidor del outbox (ADR-0012).
 *
 * @param eventId identificador unico del evento
 * @param occurredAt instante en el que ocurrio el hecho (reloj de dominio)
 * @param registrationId identificador del agregado
 *     {@link com.tfp.timetracking.tenant.domain.TenantRegistration}
 * @param email destinatario, ya normalizado
 * @param ownerFirstName nombre del propietario, para personalizar el correo
 * @param verificationToken token en claro; nunca debe registrarse en un log
 * @param expiresAt instante de caducidad del token
 * @param resend {@code true} si es un reenvío y no el envío inicial
 */
public record TenantRegistrationVerificationRequested(
        UUID eventId,
        Instant occurredAt,
        UUID registrationId,
        String email,
        String ownerFirstName,
        String verificationToken,
        Instant expiresAt,
        boolean resend) {}
