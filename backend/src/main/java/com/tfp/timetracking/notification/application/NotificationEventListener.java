package com.tfp.timetracking.notification.application;

import com.tfp.timetracking.notification.application.NotificationRecipients.FromPayload;
import com.tfp.timetracking.notification.application.NotificationRecipients.PlatformAdmins;
import com.tfp.timetracking.notification.application.NotificationRecipients.RecipientResolver;
import com.tfp.timetracking.notification.application.NotificationRecipients.TenantRole;
import com.tfp.timetracking.notification.application.NotificationTexts.ActorNames;
import com.tfp.timetracking.notification.application.NotificationTexts.BodyText;
import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.notification.domain.NotificationType;
import com.tfp.timetracking.outbox.application.IntegrationEventListener;
import com.tfp.timetracking.outbox.application.ProcessedEventStore;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea notificaciones a partir de los eventos de integracion (T110-04, T170-03,
 * RF-NOT-003).
 *
 * <p>Es idempotente: reserva el par {@code (eventId, consumidor)} antes de
 * crear nada. Hace falta porque la entrega es at-least-once y, desde que el
 * publicador propaga los fallos, el reintento reejecuta tambien a los
 * consumidores que ya habian terminado bien; sin la reserva, el usuario veria
 * la misma notificacion repetida. La reserva es <b>una sola por evento</b>
 * aunque el evento produzca varias notificaciones: todas se crean bajo ella y
 * en la misma transaccion.
 *
 * <p><b>Punto de extension:</b> para notificar un hecho nuevo basta anadir su
 * entrada a {@link #TEMPLATES}. Cada plantilla declara a quien va dirigida
 * ({@link NotificationRecipients}), si ademas se envia por correo y a que
 * pantalla lleva. Un mismo evento puede tener varias plantillas: la anomalia de
 * jornada avisa al empleado con correo y a sus administradores sin el.
 *
 * <p>Aqui esta la mecanica; la redaccion vive en {@link NotificationTexts}.
 */
@Component
public class NotificationEventListener implements IntegrationEventListener, RecipientResolver {

    /** Identifica a este consumidor en la tabla de deduplicacion. */
    static final String CONSUMER = "notification-event-listener";

    private static final String TENANT_ADMIN = "TENANT_ADMIN";

    /** Traduccion de tipo de evento a las notificaciones que produce. */
    private static final Map<String, List<Template>> TEMPLATES = templates();

    private final NotificationRepository notificationRepository;
    private final UserDirectoryQuery userDirectoryQuery;
    private final RoleRecipientQuery roleRecipientQuery;
    private final ProcessedEventStore processedEventStore;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public NotificationEventListener(
            NotificationRepository notificationRepository,
            UserDirectoryQuery userDirectoryQuery,
            RoleRecipientQuery roleRecipientQuery,
            ProcessedEventStore processedEventStore,
            Clock clock,
            IdGenerator idGenerator) {
        this.notificationRepository = notificationRepository;
        this.userDirectoryQuery = userDirectoryQuery;
        this.roleRecipientQuery = roleRecipientQuery;
        this.processedEventStore = processedEventStore;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public void onEvent(IntegrationEvent event) {
        List<Template> templates = TEMPLATES.get(event.eventType());
        if (templates == null || event.tenantId() == null) {
            return;
        }
        // Los destinatarios se resuelven antes de reservar el evento a
        // proposito: si no hay a quien avisar no se ha hecho nada, y consumir
        // la reserva impediria notificar si el evento se reentrega cuando ya
        // exista un destinatario.
        List<Pending> pending = new ArrayList<>();
        for (Template template : templates) {
            List<NotificationRecipient> recipients = template.recipients().resolve(event, this);
            if (!recipients.isEmpty()) {
                pending.add(new Pending(template, recipients));
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        if (!processedEventStore.tryClaim(event.eventId(), CONSUMER)) {
            return;
        }
        ActorNames actors = actorNames(event);
        for (Pending each : pending) {
            // El cuerpo se redacta una vez por plantilla, no una por
            // destinatario: no depende de quien lo recibe, y renderizarlo dentro
            // del bucle repetiria las consultas de nombres por cada admin.
            String body = each.template().body().render(event, actors);
            for (NotificationRecipient recipient : each.recipients()) {
                notificationRepository.save(Notification.create(
                        event.tenantId(),
                        recipient.userId(),
                        recipient.email(),
                        each.template().type(),
                        each.template().title(),
                        body,
                        each.template().emailRequired(),
                        each.template().actionPath(),
                        clock.now(),
                        idGenerator));
            }
        }
    }

    /**
     * Resuelve nombres de personas para los textos, con memoria por evento: dos
     * plantillas del mismo evento suelen nombrar al mismo empleado.
     */
    private ActorNames actorNames(IntegrationEvent event) {
        Map<String, String> resueltos = new HashMap<>();
        return field -> resueltos.computeIfAbsent(field, campo -> {
            UUID userId = NotificationTexts.uuid(event, campo);
            if (userId == null) {
                return NotificationTexts.anonimo();
            }
            return userDirectoryQuery
                    .findDisplayName(event.tenantId(), userId)
                    .orElseGet(NotificationTexts::anonimo);
        });
    }

    @Override
    public List<NotificationRecipient> byId(IntegrationEvent event, String payloadField) {
        UUID userId = NotificationTexts.uuid(event, payloadField);
        if (userId == null) {
            return List.of();
        }
        return List.of(new NotificationRecipient(
                userId, userDirectoryQuery.findEmail(event.tenantId(), userId).orElse(null)));
    }

    @Override
    public List<NotificationRecipient> byTenantRole(IntegrationEvent event, String role, String excludedField) {
        UUID excluded = excludedField == null ? null : NotificationTexts.uuid(event, excludedField);
        return roleRecipientQuery.findActiveByRole(event.tenantId(), role).stream()
                .filter(recipient -> !recipient.userId().equals(excluded))
                .toList();
    }

    @Override
    public List<NotificationRecipient> platformAdmins() {
        return roleRecipientQuery.findActivePlatformAdmins();
    }

    private static Map<String, List<Template>> templates() {
        return Map.ofEntries(
                Map.entry(
                        "corrections.correction-requested.v1",
                        List.of(new Template(
                                NotificationType.CORRECTION_REQUESTED,
                                new TenantRole(TENANT_ADMIN, "requestedBy"),
                                true,
                                "/admin/corrections",
                                "Corrección pendiente de revisar",
                                NotificationTexts::correctionRequested))),
                Map.entry(
                        "corrections.correction-approved.v1",
                        List.of(new Template(
                                NotificationType.CORRECTION_APPROVED,
                                new FromPayload("employeeId"),
                                true,
                                "/corrections",
                                "Corrección aprobada",
                                NotificationTexts::correctionApproved))),
                Map.entry(
                        "corrections.correction-rejected.v1",
                        List.of(new Template(
                                NotificationType.CORRECTION_REJECTED,
                                new FromPayload("employeeId"),
                                true,
                                "/corrections",
                                "Corrección rechazada",
                                NotificationTexts::correctionRejected))),
                Map.entry(
                        "absence.absence-requested.v1",
                        List.of(new Template(
                                NotificationType.ABSENCE_REQUESTED,
                                new TenantRole(TENANT_ADMIN, "employeeId"),
                                true,
                                "/admin/absences",
                                "Ausencia pendiente de resolver",
                                NotificationTexts::absenceRequested))),
                Map.entry(
                        "absence.absence-approved.v1",
                        List.of(new Template(
                                NotificationType.ABSENCE_APPROVED,
                                new FromPayload("employeeId"),
                                true,
                                "/absences",
                                "Ausencia aprobada",
                                NotificationTexts::absenceApproved))),
                Map.entry(
                        "absence.absence-rejected.v1",
                        List.of(new Template(
                                NotificationType.ABSENCE_REJECTED,
                                new FromPayload("employeeId"),
                                true,
                                "/absences",
                                "Ausencia rechazada",
                                NotificationTexts::absenceRejected))),
                Map.entry(
                        "time-tracking.workday-anomaly-detected.v1",
                        List.of(
                                new Template(
                                        NotificationType.WORKDAY_ANOMALY_DETECTED,
                                        new FromPayload("employeeId"),
                                        true,
                                        "/workdays",
                                        "Revisa tu última jornada",
                                        NotificationTexts::workdayAnomaly),
                                // Sin correo a proposito: en un tenant con
                                // cincuenta empleados las anomalias diarias
                                // convertirian el buzon del administrador en
                                // ruido. In-app sigue apareciendo en su bandeja.
                                new Template(
                                        NotificationType.TEAM_WORKDAY_ANOMALY,
                                        new TenantRole(TENANT_ADMIN, "employeeId"),
                                        false,
                                        "/admin/reports",
                                        "Incidencia en la jornada de un empleado",
                                        NotificationTexts::teamWorkdayAnomaly))),
                Map.entry(
                        "identity.employee-created.v1",
                        List.of(new Template(
                                NotificationType.ACCOUNT_CREATED,
                                new FromPayload("employeeId"),
                                true,
                                // No transporta credenciales: lleva a la
                                // pantalla desde la que la persona establece su
                                // propia contrasena.
                                "/auth/recuperar-contrasena",
                                "Tu cuenta ya está lista",
                                NotificationTexts::accountCreated))),
                Map.entry(
                        "identity.employee-deactivated.v1",
                        List.of(new Template(
                                NotificationType.ACCOUNT_DEACTIVATED,
                                new FromPayload("employeeId"),
                                true,
                                null,
                                "Tu cuenta se ha desactivado",
                                NotificationTexts::accountDeactivated))),
                Map.entry(
                        "shift.shift-assigned.v1",
                        List.of(new Template(
                                NotificationType.SHIFT_ASSIGNED,
                                new FromPayload("employeeId"),
                                true,
                                "/shifts",
                                "Tienes un turno nuevo",
                                NotificationTexts::shiftAssigned))),
                Map.entry(
                        "tenant.suspended.v1",
                        List.of(new Template(
                                NotificationType.TENANT_SUSPENDED,
                                new TenantRole(TENANT_ADMIN),
                                true,
                                null,
                                "Tu organización se ha suspendido",
                                NotificationTexts::tenantSuspended))),
                Map.entry(
                        "tenant.reactivated.v1",
                        List.of(new Template(
                                NotificationType.TENANT_REACTIVATED,
                                new TenantRole(TENANT_ADMIN),
                                true,
                                null,
                                "Tu organización vuelve a estar activa",
                                NotificationTexts::tenantReactivated))),
                Map.entry(
                        "tenant.archived.v1",
                        List.of(new Template(
                                NotificationType.TENANT_ARCHIVED,
                                new TenantRole(TENANT_ADMIN),
                                true,
                                null,
                                "Tu organización se ha archivado",
                                NotificationTexts::tenantArchived))),
                Map.entry(
                        "tenant.registration-email-verified.v1",
                        List.of(new Template(
                                NotificationType.REGISTRATION_PENDING_REVIEW,
                                new PlatformAdmins(),
                                true,
                                "/platform/registrations",
                                "Alta pendiente de revisión",
                                NotificationTexts::registrationPendingReview))));
    }

    /**
     * Una notificacion que produce un evento.
     *
     * @param type tipo del catalogo, que el frontend traduce a texto e icono
     * @param recipients a quien va dirigida
     * @param emailRequired si ademas del aviso in-app sale un correo
     * @param actionPath ruta del frontend a la que lleva, o {@code null}
     * @param title titulo in-app, que es tambien el asunto del correo
     * @param body redaccion del cuerpo, de {@link NotificationTexts}
     */
    private record Template(
            NotificationType type,
            NotificationRecipients recipients,
            boolean emailRequired,
            String actionPath,
            String title,
            BodyText body) {}

    /** Plantilla ya emparejada con sus destinatarios. */
    private record Pending(Template template, List<NotificationRecipient> recipients) {}
}
