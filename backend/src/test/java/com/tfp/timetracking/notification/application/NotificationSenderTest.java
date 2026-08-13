package com.tfp.timetracking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.notification.domain.NotificationStatus;
import com.tfp.timetracking.notification.domain.NotificationType;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationSenderTest {

    private static final Instant NOW = Instant.parse("2026-08-06T10:00:00Z");

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationEmailComposer composer = mock(NotificationEmailComposer.class);

    @Test
    void marksAsSentWhenTheEmailGoesOut() {
        NotificationSender sender = sender(3);
        Notification notification = pending("empleado@acme.test");

        assertThat(sender.deliver(notification)).isTrue();

        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
        verify(repository).save(notification);
    }

    @Test
    void keepsItPendingAfterARecoverableFailure() {
        NotificationSender sender = sender(3);
        doThrow(new RuntimeException("SMTP caido")).when(composer).send(any());
        Notification notification = pending("empleado@acme.test");

        assertThat(sender.deliver(notification)).isFalse();

        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.attempts()).isEqualTo(1);
        verify(repository).save(notification);
    }

    @Test
    void marksAsFailedAfterExhaustingTheAttempts() {
        NotificationSender sender = sender(1);
        doThrow(new RuntimeException("SMTP caido")).when(composer).send(any());
        Notification notification = pending("empleado@acme.test");

        sender.deliver(notification);

        assertThat(notification.status()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void neverPropagatesTheFailure() {
        // Agotar los reintentos es un desenlace previsto, no un error del
        // proceso: si se propagase, tumbaria el lote entero.
        NotificationSender sender = sender(1);
        doThrow(new RuntimeException("SMTP caido")).when(composer).send(any());

        assertThat(sender.deliver(pending("empleado@acme.test"))).isFalse();
    }

    @Test
    void skipsNotificationsWithoutARecipientEmail() {
        NotificationSender sender = sender(3);
        Notification notification = pending(null);

        assertThat(sender.deliver(notification)).isFalse();

        verify(composer, never()).send(any());
        verify(repository, never()).save(any());
    }

    private NotificationSender sender(int maxAttempts) {
        return new NotificationSender(
                repository,
                composer,
                new NotificationDeliveryProperties(true, Duration.ofSeconds(30), 50, maxAttempts),
                () -> NOW);
    }

    private Notification pending(String email) {
        return Notification.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                email,
                NotificationType.ABSENCE_APPROVED,
                "Ausencia aprobada",
                "Tu solicitud ha sido aprobada.",
                true,
                "/absences",
                NOW,
                UUID::randomUUID);
    }
}
