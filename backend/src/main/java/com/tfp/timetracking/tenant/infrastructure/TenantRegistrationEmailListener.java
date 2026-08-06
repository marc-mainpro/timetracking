package com.tfp.timetracking.tenant.infrastructure;

import com.tfp.timetracking.notification.application.EmailMessage;
import com.tfp.timetracking.notification.application.EmailSender;
import com.tfp.timetracking.outbox.application.IntegrationEventListener;
import com.tfp.timetracking.outbox.application.ProcessedEventStore;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.tenant.application.RegistrationProperties;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Envía el correo de verificación del alta pública (T53-05, RF-REG-004).
 *
 * <p>Vive <b>fuera</b> de la transacción de negocio, colgado del Outbox
 * (ADR-0012): el caso de uso solo persiste la solicitud y publica su evento; el
 * SMTP se contacta después, cuando el publicador entrega
 * {@code tenant.registration-verification-requested.v1}. Un servidor de correo
 * lento o caído no alarga ni deshace el alta.
 *
 * <p>Nunca registra el cuerpo del mensaje ni el token (RS-014); solo el
 * identificador de la solicitud.
 */
@Component
public class TenantRegistrationEmailListener implements IntegrationEventListener {

    static final String EVENT_TYPE = "tenant.registration-verification-requested.v1";
    private static final String SUBJECT = "Confirma tu dirección de correo";

    private static final Logger log = LoggerFactory.getLogger(TenantRegistrationEmailListener.class);

    /** Identifica a este consumidor en la tabla de deduplicacion. */
    static final String CONSUMER = "tenant-registration-email";

    private final ProcessedEventStore processedEventStore;
    private final EmailSender emailSender;
    private final RegistrationProperties properties;

    public TenantRegistrationEmailListener(
            ProcessedEventStore processedEventStore, EmailSender emailSender, RegistrationProperties properties) {
        this.processedEventStore = processedEventStore;
        this.emailSender = emailSender;
        this.properties = properties;
    }

    @Override
    public void onEvent(IntegrationEvent event) {
        if (!EVENT_TYPE.equals(event.eventType())) {
            return;
        }
        String to = string(event, "email");
        String token = string(event, "verificationToken");
        if (to == null || token == null) {
            log.warn("registration.email.skipped-incomplete-event eventId={}", event.eventId());
            return;
        }
        if (!processedEventStore.tryClaim(event.eventId(), CONSUMER)) {
            log.info("registration.email.already-sent registrationId={}", event.aggregateId());
            return;
        }
        emailSender.send(new EmailMessage(to, SUBJECT, body(string(event, "ownerFirstName"), token)));
        log.info("registration.email.sent registrationId={}", event.aggregateId());
    }

    private String body(String ownerFirstName, String token) {
        String url = String.format(properties.verificationUrlTemplate(), token);
        String greeting = ownerFirstName == null || ownerFirstName.isBlank() ? "Hola" : "Hola " + ownerFirstName;
        return greeting
                + ",\n\nHemos recibido una solicitud de alta con esta dirección de correo.\n"
                + "Para continuar, confirma que es tuya:\n\n"
                + url
                + "\n\nEl enlace caduca en "
                + properties.verification().tokenTtl().toHours()
                + " horas y solo puede usarse una vez.\n"
                + "Si no has solicitado nada, ignora este mensaje: no se creará ninguna cuenta.\n";
    }

    private static String string(IntegrationEvent event, String key) {
        return Objects.toString(event.payload().get(key), null);
    }
}
