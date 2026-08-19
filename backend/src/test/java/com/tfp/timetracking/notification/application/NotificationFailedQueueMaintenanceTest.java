package com.tfp.timetracking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationNotFailedException;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.notification.domain.NotificationStatus;
import com.tfp.timetracking.notification.domain.NotificationType;
import com.tfp.timetracking.outbox.application.FailedQueueMaintenance.FailedQueueEntry;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationFailedQueueMaintenanceTest {

    private static final Instant NOW = Instant.parse("2026-08-06T10:00:00Z");

    @Mock
    private NotificationRepository repository;

    @Test
    void reportsTheQueueNameThePanelAlreadyShows() {
        assertThat(new NotificationFailedQueueMaintenance(repository).queueName()).isEqualTo("notifications");
    }

    @Test
    void listedEntriesNeverCarryPersonalData() {
        NotificationFailedQueueMaintenance maintenance = new NotificationFailedQueueMaintenance(repository);
        Notification failed = failed();
        when(repository.findByStatus(NotificationStatus.FAILED, 0, 20))
                .thenReturn(new PagedResult<>(List.of(failed), 0, 20, 1, 1));

        PagedResult<FailedQueueEntry> result = maintenance.listFailed(0, 20);

        FailedQueueEntry entry = result.content().get(0);
        // Un PLATFORM_ADMIN interviene en la cola de todos los tenants: le basta
        // el tipo y el destinatario, no el cuerpo ni el correo de nadie.
        assertThat(entry.type()).isEqualTo(NotificationType.CORRECTION_APPROVED.name());
        assertThat(entry.reference()).isEqualTo(failed.recipientUserId().toString());
        assertThat(entry.lastError()).isEqualTo("SMTP caido");
    }

    @Test
    void retryingPutsTheNotificationBackInTheDeliveryQueue() {
        NotificationFailedQueueMaintenance maintenance = new NotificationFailedQueueMaintenance(repository);
        Notification failed = failed();
        when(repository.findByIdForPlatform(failed.id())).thenReturn(Optional.of(failed));
        when(repository.requeueFailed(failed.id())).thenReturn(true);

        FailedQueueEntry previous = maintenance.retry(failed.id());

        // Devuelve la foto previa (con el error) y deja el agregado reencolado.
        assertThat(previous.lastError()).isEqualTo("SMTP caido");
        assertThat(failed.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(failed.isDeliverable()).isTrue();
        verify(repository).requeueFailed(failed.id());
    }

    @Test
    void discardingGivesUpOnTheEmailButNotOnTheNotice() {
        NotificationFailedQueueMaintenance maintenance = new NotificationFailedQueueMaintenance(repository);
        Notification failed = failed();
        when(repository.findByIdForPlatform(failed.id())).thenReturn(Optional.of(failed));
        when(repository.discardFailed(failed.id())).thenReturn(true);

        maintenance.discard(failed.id());

        assertThat(failed.status()).isEqualTo(NotificationStatus.DISCARDED);
        // El aviso sigue existiendo para su destinatario: lo que se abandona es
        // el envio por correo, no el hecho que lo motivo.
        assertThat(failed.lastError()).isEqualTo("SMTP caido");
        verify(repository).discardFailed(failed.id());
    }

    @Test
    void anUnknownNotificationIsNotFound() {
        NotificationFailedQueueMaintenance maintenance = new NotificationFailedQueueMaintenance(repository);
        UUID id = UUID.randomUUID();
        when(repository.findByIdForPlatform(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> maintenance.retry(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).requeueFailed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aNotificationThatIsNotFailedIsRejected() {
        NotificationFailedQueueMaintenance maintenance = new NotificationFailedQueueMaintenance(repository);
        Notification pending = sample();
        when(repository.findByIdForPlatform(pending.id())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> maintenance.discard(pending.id()))
                .isInstanceOf(NotificationNotFailedException.class);
        verify(repository, never()).discardFailed(org.mockito.ArgumentMatchers.any());

    }

    @Test
    void aConcurrentInterventionIsRejected() {
        NotificationFailedQueueMaintenance maintenance = new NotificationFailedQueueMaintenance(repository);
        Notification failed = failed();
        Notification discarded = failed();
        discarded.discardDelivery();
        when(repository.findByIdForPlatform(failed.id()))
                .thenReturn(Optional.of(failed), Optional.of(discarded));
        when(repository.requeueFailed(failed.id())).thenReturn(false);

        assertThatThrownBy(() -> maintenance.retry(failed.id()))
                .isInstanceOf(NotificationNotFailedException.class);
    }

    @Test
    void listingIsAlwaysBoundedByThePageRequested() {
        NotificationFailedQueueMaintenance maintenance = new NotificationFailedQueueMaintenance(repository);
        when(repository.findByStatus(org.mockito.ArgumentMatchers.eq(NotificationStatus.FAILED), anyInt(), anyInt()))
                .thenReturn(new PagedResult<>(List.of(), 2, 5, 0, 0));

        PagedResult<FailedQueueEntry> result = maintenance.listFailed(2, 5);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
    }

    private Notification failed() {
        Notification notification = sample();
        notification.markAttemptFailed("SMTP caido", 1, NOW);
        return notification;
    }

    private Notification sample() {
        return Notification.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "empleado@acme.test",
                NotificationType.CORRECTION_APPROVED,
                "Corrección aprobada",
                "Tu solicitud ha sido aprobada.",
                true,
                "/corrections",
                NOW,
                UUID::randomUUID);
    }
}
