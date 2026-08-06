package com.tfp.timetracking.notification.application;

import com.tfp.timetracking.notification.domain.Notification;
import org.springframework.stereotype.Component;

/**
 * Convierte una notificacion en el correo que la transporta (T110-05).
 *
 * <p>Separado de {@link NotificationSender} para que el que decide *que* se
 * escribe no sea el mismo que decide *cuando* se reintenta.
 */
@Component
public class NotificationEmailComposer {

    private final EmailSender emailSender;

    public NotificationEmailComposer(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void send(Notification notification) {
        emailSender.send(new EmailMessage(
                notification.recipientEmail(), notification.title(), notification.body()));
    }
}
