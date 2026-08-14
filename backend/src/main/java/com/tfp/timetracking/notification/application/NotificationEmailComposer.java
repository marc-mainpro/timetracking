package com.tfp.timetracking.notification.application;

import com.tfp.timetracking.notification.domain.Notification;
import org.springframework.stereotype.Component;

/**
 * Convierte una notificacion en el correo que la transporta (T110-05, T170-09).
 *
 * <p>Separado de {@link NotificationSender} para que el que decide *que* se
 * escribe no sea el mismo que decide *cuando* se reintenta.
 *
 * <p>El cuerpo se compone aqui y no se guarda compuesto en la notificacion: el
 * mismo hecho se lee dentro de la aplicacion, donde el saludo, el enlace y el
 * pie sobran porque ya esta uno dentro. Texto plano, coherente con los correos
 * de verificacion y recuperacion ya existentes.
 */
@Component
public class NotificationEmailComposer {

    /**
     * El saludo va sin nombre a proposito. Personalizarlo obligaria a guardar el
     * nombre del destinatario junto a la notificacion —el emisor no puede
     * consultarlo en cada intento de envio sin contradecir el motivo por el que
     * el correo se guarda desnormalizado—, y no compensa una columna y un dato
     * personal duplicado por una linea de cortesia.
     */
    private static final String GREETING = "Hola:";

    private static final String FOOTER =
            "Mensaje automático del sistema de control horario. No hace falta que respondas.";

    private final EmailSender emailSender;
    private final NotificationEmailProperties properties;

    public NotificationEmailComposer(EmailSender emailSender, NotificationEmailProperties properties) {
        this.emailSender = emailSender;
        this.properties = properties;
    }

    public void send(Notification notification) {
        emailSender.send(new EmailMessage(notification.recipientEmail(), notification.title(), body(notification)));
    }

    private String body(Notification notification) {
        StringBuilder body = new StringBuilder(GREETING).append("\n\n").append(notification.body());
        String link = link(notification);
        if (link != null) {
            body.append("\n\nAbrirlo en la aplicación:\n").append(link);
        }
        return body.append("\n\n—\n").append(FOOTER).toString();
    }

    /**
     * @return la URL absoluta a la que lleva la notificacion, o {@code null} si
     *     la notificacion no lleva a ninguna pantalla o no hay base configurada
     */
    private String link(Notification notification) {
        if (notification.actionPath() == null || properties.appBaseUrl() == null) {
            return null;
        }
        return properties.appBaseUrl() + notification.actionPath();
    }
}
