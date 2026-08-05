package com.tfp.timetracking.identity.infrastructure;

import com.tfp.timetracking.identity.application.PasswordResetProperties;
import com.tfp.timetracking.notification.application.EmailMessage;
import com.tfp.timetracking.notification.application.EmailSender;
import com.tfp.timetracking.outbox.application.IntegrationEventListener;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetEmailListener implements IntegrationEventListener {

    static final String EVENT_TYPE = "identity.password-reset-requested.v1";
    private static final String SUBJECT = "Restablece tu contrasena";
    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailListener.class);

    private final EmailSender emailSender;
    private final PasswordResetProperties properties;

    public PasswordResetEmailListener(EmailSender emailSender, PasswordResetProperties properties) {
        this.emailSender = emailSender;
        this.properties = properties;
    }

    @Override
    public void onEvent(IntegrationEvent event) {
        if (!EVENT_TYPE.equals(event.eventType())) {
            return;
        }
        String email = string(event, "email");
        String resetToken = string(event, "resetToken");
        if (email == null || resetToken == null) {
            log.warn("password-reset.email.skipped-incomplete-event eventId={}", event.eventId());
            return;
        }
        emailSender.send(new EmailMessage(email, SUBJECT, body(string(event, "firstName"), resetToken)));
        log.info("password-reset.email.sent aggregateId={}", event.aggregateId());
    }

    private String body(String firstName, String token) {
        String greeting = firstName == null || firstName.isBlank() ? "Hola" : "Hola " + firstName;
        String url = String.format(properties.resetUrlTemplate(), token);
        return greeting
                + ",\n\nHas solicitado restablecer tu contrasena.\n"
                + "Para continuar, abre este enlace:\n\n"
                + url
                + "\n\nEl enlace caduca en "
                + properties.tokenTtl().toMinutes()
                + " minutos y solo puede usarse una vez.\n"
                + "Si no has solicitado este cambio, ignora este mensaje.\n";
    }

    private static String string(IntegrationEvent event, String key) {
        return Objects.toString(event.payload().get(key), null);
    }
}
