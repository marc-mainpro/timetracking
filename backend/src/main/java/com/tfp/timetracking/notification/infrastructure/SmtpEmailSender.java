package com.tfp.timetracking.notification.infrastructure;

import com.tfp.timetracking.notification.application.EmailDeliveryException;
import com.tfp.timetracking.notification.application.EmailMessage;
import com.tfp.timetracking.notification.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Adaptador SMTP del puerto {@link EmailSender} (T110-01, ADR-0012).
 *
 * <p>Solo se activa con {@code mail.enabled=true}. Con el flag apagado —el
 * valor por defecto, y el que usan los tests— el contenedor levanta
 * {@link LoggingEmailSender} en su lugar, de modo que ningun entorno intenta
 * abrir una conexion SMTP por accidente.
 *
 * <p>Registra el destinatario y el asunto, nunca el cuerpo (RS-014): los correos
 * de verificacion y de recuperacion de contrasena llevan tokens de un solo uso.
 */
@Component
@ConditionalOnProperty(name = "mail.enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender javaMailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender javaMailSender, @Value("${mail.from}") String from) {
        this.javaMailSender = javaMailSender;
        this.from = from;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        try {
            javaMailSender.send(mail);
            log.info("Correo enviado a {} con asunto '{}'", message.to(), message.subject());
        } catch (MailException e) {
            throw new EmailDeliveryException(
                    "No se pudo enviar el correo con asunto '" + message.subject() + "'", e);
        }
    }
}
