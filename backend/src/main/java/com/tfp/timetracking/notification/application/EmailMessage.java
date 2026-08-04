package com.tfp.timetracking.notification.application;

/**
 * Mensaje de correo listo para enviar (T110-01, ADR-0012).
 *
 * <p>Deliberadamente plano: destinatario, asunto y cuerpo ya renderizados. La
 * eleccion de plantilla y el idioma se resuelven antes de llegar aqui, de modo
 * que el adaptador SMTP no conozca nada del dominio.
 *
 * @param to destinatario
 * @param subject asunto
 * @param body cuerpo en texto plano; puede contener tokens de un solo uso, por
 *     lo que nunca debe registrarse en logs (RS-014)
 */
public record EmailMessage(String to, String subject, String body) {

    public EmailMessage {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("El destinatario es obligatorio");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("El asunto es obligatorio");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("El cuerpo es obligatorio");
        }
    }
}
