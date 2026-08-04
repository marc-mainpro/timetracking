package com.tfp.timetracking.notification.application;

/**
 * El envio de correo no se pudo completar (T110-01, ADR-0012).
 *
 * <p>No hereda de {@code DomainException} a proposito: no es una regla de
 * negocio violada por el usuario, es un fallo de infraestructura. Nunca debe
 * traducirse a una respuesta HTTP 4xx ni llegar al borde REST, porque el envio
 * ocurre fuera de la peticion, en el consumidor del Outbox, que es quien decide
 * si reintenta.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
