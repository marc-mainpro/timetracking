package com.tfp.timetracking.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-08-06T10:00:00Z");

    @Test
    void isCreatedPendingAndUnread() {
        Notification notification = sample("empleado@acme.test");

        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.attempts()).isZero();
        assertThat(notification.isDeliverable()).isTrue();
    }

    @Test
    void isNotDeliverableWithoutARecipientEmail() {
        // Sin correo no hay nada que enviar, pero la notificacion sigue siendo
        // valida y visible en la aplicacion.
        assertThat(sample(null).isDeliverable()).isFalse();
        assertThat(sample("  ").isDeliverable()).isFalse();
    }

    @Test
    void markSentRecordsTheAttempt() {
        Notification notification = sample("empleado@acme.test");

        notification.markSent(NOW);

        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.sentAt()).isEqualTo(NOW);
        assertThat(notification.attempts()).isEqualTo(1);
        assertThat(notification.isDeliverable()).isFalse();
    }

    @Test
    void staysPendingWhileThereAreAttemptsLeft() {
        Notification notification = sample("empleado@acme.test");

        notification.markAttemptFailed("SMTP caido", 3, NOW);

        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.attempts()).isEqualTo(1);
        assertThat(notification.lastError()).isEqualTo("SMTP caido");
        assertThat(notification.isDeliverable()).isTrue();
    }

    @Test
    void failsAfterExhaustingTheAttempts() {
        Notification notification = sample("empleado@acme.test");

        notification.markAttemptFailed("boom", 2, NOW);
        notification.markAttemptFailed("boom", 2, NOW);

        assertThat(notification.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.attempts()).isEqualTo(2);
        assertThat(notification.isDeliverable()).isFalse();
    }

    @Test
    void aFailedNotificationIsStillReadable() {
        // Que el correo no saliera no significa que el usuario no deba
        // enterarse del hecho.
        Notification notification = sample("empleado@acme.test");
        notification.markAttemptFailed("boom", 1, NOW);

        notification.markRead(NOW);

        assertThat(notification.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void markReadIsIdempotentAndKeepsTheFirstDate() {
        Notification notification = sample("empleado@acme.test");
        Instant later = NOW.plusSeconds(3600);

        notification.markRead(NOW);
        notification.markRead(later);

        assertThat(notification.readAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsTransitionsOutOfATerminalState() {
        Notification sent = sample("empleado@acme.test");
        sent.markSent(NOW);

        assertThatThrownBy(() -> sent.markSent(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> sent.cancel()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> sent.markAttemptFailed("x", 3, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void truncatesAnOverlongError() {
        Notification notification = sample("empleado@acme.test");

        notification.markAttemptFailed("x".repeat(900), 5, NOW);

        assertThat(notification.lastError()).hasSize(500);
    }

    @Test
    void rejectsMissingTitleOrBody() {
        assertThatThrownBy(() -> Notification.create(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "a@acme.test",
                        NotificationType.ABSENCE_APPROVED,
                        " ",
                        "cuerpo",
                        true,
                        null,
                        NOW,
                        UUID::randomUUID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.create(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "a@acme.test",
                        NotificationType.ABSENCE_APPROVED,
                        "titulo",
                        "",
                        true,
                        null,
                        NOW,
                        UUID::randomUUID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requeueingAFailedDeliveryStartsTheAttemptsOver() {
        Notification notification = failed();

        notification.requeueDelivery();

        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.attempts()).isZero();
        assertThat(notification.lastError()).isNull();
        // Vuelve a ser trabajo de la cola de envio, no solo un cambio de estado.
        assertThat(notification.isDeliverable()).isTrue();
    }

    @Test
    void discardingAFailedDeliveryKeepsTheTrace() {
        Notification notification = failed();

        notification.discardDelivery();

        assertThat(notification.status()).isEqualTo(NotificationStatus.DISCARDED);
        // El error se conserva: es la evidencia de por que hubo que descartarla.
        assertThat(notification.lastError()).isEqualTo("SMTP caido");
        // Y deja de ser trabajo pendiente para el emisor.
        assertThat(notification.isDeliverable()).isFalse();
    }

    @Test
    void onlyFailedDeliveriesCanBeRequeuedOrDiscarded() {
        Notification pending = sample("empleado@acme.test");
        assertThatThrownBy(pending::requeueDelivery).isInstanceOf(NotificationNotFailedException.class);
        assertThatThrownBy(pending::discardDelivery).isInstanceOf(NotificationNotFailedException.class);

        Notification sent = sample("empleado@acme.test");
        sent.markSent(NOW);
        assertThatThrownBy(sent::requeueDelivery).isInstanceOf(NotificationNotFailedException.class);

        // Descartar dos veces tampoco: la segunda ya no encuentra un fallo que
        // abandonar, y silenciarlo ocultaria que otro admin se adelanto.
        Notification discarded = failed();
        discarded.discardDelivery();
        assertThatThrownBy(discarded::discardDelivery).isInstanceOf(NotificationNotFailedException.class);
    }

    @Test
    void notFailedExceptionCarriesTheStableErrorCode() {
        Notification pending = sample("empleado@acme.test");

        assertThatThrownBy(pending::discardDelivery)
                .isInstanceOf(NotificationNotFailedException.class)
                .extracting(error -> ((NotificationNotFailedException) error).errorCode())
                .isEqualTo("NOTIFICATION_NOT_FAILED");
    }

    /** Una notificacion que ya agoto sus reintentos de envio. */
    private Notification failed() {
        Notification notification = sample("empleado@acme.test");
        notification.markAttemptFailed("SMTP caido", 1, NOW);
        return notification;
    }

    private Notification sample(String email) {
        return Notification.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                email,
                NotificationType.CORRECTION_APPROVED,
                "Corrección aprobada",
                "Tu solicitud ha sido aprobada.",
                true,
                "/corrections",
                NOW,
                UUID::randomUUID);
    }
}
