package com.tfp.timetracking.notification.application;

/**
 * Puerto de salida para el envio de correo (T110-01, ADR-0012).
 *
 * <p>Lo consumen los flujos que necesitan alcanzar al usuario fuera de la
 * sesion HTTP: verificacion de correo del alta publica (RF-REG-004),
 * recuperacion de contrasena (RF-USR-006) y notificaciones (RF-NOT-002).
 *
 * <p><b>El envio nunca forma parte de la transaccion de negocio.</b> Es I/O de
 * red contra un tercero: si se invocase dentro de la transaccion, un SMTP lento
 * mantendria abierta una transaccion de base de datos, y un fallo de envio haria
 * rollback de un cambio de negocio que ya es valido. Los casos de uso publican
 * su evento de dominio, este viaja por el Outbox (ADR-0005) y es el consumidor
 * interno quien llama a este puerto, con reintentos e idempotencia.
 *
 * <p>Las implementaciones deben ser <b>seguras ante fallos parciales</b>: lanzar
 * {@link EmailDeliveryException} cuando el envio no se pueda completar, para que
 * el consumidor decida si reintenta. No deben tragarse el error en silencio ni
 * registrar el cuerpo del mensaje (RS-014: puede contener tokens de un solo uso).
 */
public interface EmailSender {

    /**
     * @param message mensaje a enviar
     * @throws EmailDeliveryException si el envio no se pudo completar
     */
    void send(EmailMessage message);
}
