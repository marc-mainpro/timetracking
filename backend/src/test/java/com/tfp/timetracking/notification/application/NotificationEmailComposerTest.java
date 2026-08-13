package com.tfp.timetracking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationEmailComposerTest {

    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");

    private final List<EmailMessage> sent = new ArrayList<>();
    private final EmailSender emailSender = sent::add;

    @Test
    void includesAnAbsoluteLinkToTheScreenOfTheNotification() {
        composer("https://app.tfp.test").send(notification("/admin/corrections"));

        assertThat(sent).singleElement().satisfies(message -> {
            assertThat(message.to()).isEqualTo("admin@acme.test");
            // El asunto es el titulo de la notificacion, sin adornos.
            assertThat(message.subject()).isEqualTo("Corrección pendiente de revisar");
            assertThat(message.body())
                    .startsWith("Hola:")
                    .contains("El cuerpo de la notificación.")
                    .contains("Abrirlo en la aplicación:")
                    .contains("https://app.tfp.test/admin/corrections")
                    .endsWith("No hace falta que respondas.");
        });
    }

    @Test
    void doesNotDuplicateTheSlashWhenTheBaseUrlEndsWithOne() {
        composer("https://app.tfp.test/").send(notification("/absences"));

        assertThat(sent).singleElement().satisfies(message ->
                assertThat(message.body()).contains("https://app.tfp.test/absences").doesNotContain("test//"));
    }

    @Test
    void producesAValidEmailForANotificationWithoutAnActionPath() {
        composer("https://app.tfp.test").send(notification(null));

        assertThat(sent).singleElement().satisfies(message -> {
            assertThat(message.body()).contains("El cuerpo de la notificación.");
            assertThat(message.body()).doesNotContain("http");
        });
    }

    @Test
    void omitsTheLinkWhenNoBaseUrlIsConfigured() {
        // Mejor un correo sin enlace que uno con un enlace roto.
        composer(null).send(notification("/absences"));

        assertThat(sent).singleElement().satisfies(message -> assertThat(message.body()).doesNotContain("http"));
    }

    private NotificationEmailComposer composer(String appBaseUrl) {
        return new NotificationEmailComposer(emailSender, new NotificationEmailProperties(appBaseUrl));
    }

    private Notification notification(String actionPath) {
        return Notification.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "admin@acme.test",
                NotificationType.CORRECTION_REQUESTED,
                "Corrección pendiente de revisar",
                "El cuerpo de la notificación.",
                true,
                actionPath,
                NOW,
                UUID::randomUUID);
    }
}
