package com.tfp.timetracking.notification.application;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.notification.domain.NotificationType;
import com.tfp.timetracking.outbox.application.IntegrationEventListener;
import com.tfp.timetracking.outbox.application.ProcessedEventStore;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea notificaciones a partir de los eventos de integracion (T110-04,
 * RF-NOT-003).
 *
 * <p>Es idempotente: reserva el par {@code (eventId, consumidor)} antes de
 * crear nada. Hace falta porque la entrega es at-least-once y, desde que el
 * publicador propaga los fallos, el reintento reejecuta tambien a los
 * consumidores que ya habian terminado bien; sin la reserva, el usuario veria
 * la misma notificacion repetida.
 *
 * <p><b>Punto de extension:</b> para notificar un hecho nuevo basta anadir su
 * entrada a {@link #TEMPLATES}. No hay que tocar el resto del modulo ni el
 * publicador. Lo unico obligatorio es que el evento lleve en su payload el
 * identificador del empleado al que va dirigido.
 */
@Component
public class NotificationEventListener implements IntegrationEventListener {

    /** Identifica a este consumidor en la tabla de deduplicacion. */
    static final String CONSUMER = "notification-event-listener";

    /** Traduccion de tipo de evento a la notificacion que produce. */
    private static final Map<String, Template> TEMPLATES = Map.of(
            "corrections.correction-approved.v1",
                    new Template(
                            NotificationType.CORRECTION_APPROVED,
                            "employeeId",
                            "Corrección aprobada",
                            event -> "Tu solicitud de corrección de jornada ha sido aprobada."),
            "corrections.correction-rejected.v1",
                    new Template(
                            NotificationType.CORRECTION_REJECTED,
                            "employeeId",
                            "Corrección rechazada",
                            event -> "Tu solicitud de corrección de jornada ha sido rechazada."),
            "absence.absence-approved.v1",
                    new Template(
                            NotificationType.ABSENCE_APPROVED,
                            "employeeId",
                            "Ausencia aprobada",
                            event -> "Tu solicitud de ausencia ha sido aprobada."),
            "absence.absence-rejected.v1",
                    new Template(
                            NotificationType.ABSENCE_REJECTED,
                            "employeeId",
                            "Ausencia rechazada",
                            event -> "Tu solicitud de ausencia ha sido rechazada."),
            "time-tracking.workday-anomaly-detected.v1",
                    new Template(
                            NotificationType.WORKDAY_ANOMALY_DETECTED,
                            "employeeId",
                            "Revisa tu jornada",
                            NotificationEventListener::anomalyBody));

    private final NotificationRepository notificationRepository;
    private final RecipientEmailQuery recipientEmailQuery;
    private final ProcessedEventStore processedEventStore;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public NotificationEventListener(
            NotificationRepository notificationRepository,
            RecipientEmailQuery recipientEmailQuery,
            ProcessedEventStore processedEventStore,
            Clock clock,
            IdGenerator idGenerator) {
        this.notificationRepository = notificationRepository;
        this.recipientEmailQuery = recipientEmailQuery;
        this.processedEventStore = processedEventStore;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public void onEvent(IntegrationEvent event) {
        Template template = TEMPLATES.get(event.eventType());
        if (template == null) {
            return;
        }
        UUID recipientUserId = uuid(event, template.recipientField());
        if (recipientUserId == null || event.tenantId() == null) {
            return;
        }
        if (!processedEventStore.tryClaim(event.eventId(), CONSUMER)) {
            return;
        }
        String recipientEmail =
                recipientEmailQuery.findEmail(event.tenantId(), recipientUserId).orElse(null);
        notificationRepository.save(Notification.create(
                event.tenantId(),
                recipientUserId,
                recipientEmail,
                template.type(),
                template.title(),
                template.body().apply(event),
                clock.now(),
                idGenerator));
    }

    private static String anomalyBody(IntegrationEvent event) {
        Object anomalies = event.payload().get("anomalies");
        if (anomalies == null || anomalies.toString().isBlank()) {
            return "Se ha detectado una incidencia en tu jornada.";
        }
        return "Se ha detectado una incidencia en tu jornada: " + anomalies + ".";
    }

    private static UUID uuid(IntegrationEvent event, String field) {
        Object value = event.payload().get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return Optional.of(value.toString())
                .filter(text -> !text.isBlank())
                .map(text -> {
                    try {
                        return UUID.fromString(text);
                    } catch (IllegalArgumentException notAUuid) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private record Template(
            NotificationType type,
            String recipientField,
            String title,
            Function<IntegrationEvent, String> body) {}
}
