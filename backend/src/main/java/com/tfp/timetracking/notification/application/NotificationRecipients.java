package com.tfp.timetracking.notification.application;

import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.List;

/**
 * Estrategia que decide a quien va dirigida una notificacion (T170-03).
 *
 * <p>Antes de esta epica el destinatario era siempre un campo del payload, asi
 * que bastaba con guardar su nombre. Con los avisos de administrador el
 * destinatario deja de estar en el evento: es «todos los que tienen tal rol»,
 * que solo sabe responder {@code identity}. Modelarlo como estrategia mantiene
 * la tabla de plantillas declarativa, sin ramas por tipo de evento.
 */
public sealed interface NotificationRecipients {

    /**
     * @param event evento que origina el aviso
     * @param resolver acceso a los usuarios; lo aporta el consumidor
     * @return los destinatarios; lista vacia si no hay nadie a quien avisar, que
     *     es un desenlace normal y no un error
     */
    List<NotificationRecipient> resolve(IntegrationEvent event, RecipientResolver resolver);

    /** Servicios que necesita la estrategia para resolver destinatarios. */
    interface RecipientResolver {

        List<NotificationRecipient> byId(IntegrationEvent event, String payloadField);

        List<NotificationRecipient> byTenantRole(IntegrationEvent event, String role, String excludedField);

        List<NotificationRecipient> platformAdmins();
    }

    /** Un unico usuario, nombrado por un campo del payload (p. ej. {@code employeeId}). */
    record FromPayload(String field) implements NotificationRecipients {
        @Override
        public List<NotificationRecipient> resolve(IntegrationEvent event, RecipientResolver resolver) {
            return resolver.byId(event, field);
        }
    }

    /**
     * Todos los usuarios activos del tenant del evento que tienen un rol.
     *
     * <p>{@code excludedField} nombra, cuando procede, al usuario que provoco el
     * hecho: quien pide una correccion no necesita que le avisen de su propia
     * solicitud aunque ademas sea administrador.
     */
    record TenantRole(String role, String excludedField) implements NotificationRecipients {

        public TenantRole(String role) {
            this(role, null);
        }

        @Override
        public List<NotificationRecipient> resolve(IntegrationEvent event, RecipientResolver resolver) {
            return resolver.byTenantRole(event, role, excludedField);
        }
    }

    /** Todos los administradores de plataforma activos. */
    record PlatformAdmins() implements NotificationRecipients {
        @Override
        public List<NotificationRecipient> resolve(IntegrationEvent event, RecipientResolver resolver) {
            return resolver.platformAdmins();
        }
    }
}
