package com.tfp.timetracking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.notification.domain.NotificationType;
import com.tfp.timetracking.outbox.application.GetSystemStatusUseCase;
import com.tfp.timetracking.outbox.application.GetSystemStatusUseCase.SystemStatus;
import com.tfp.timetracking.outbox.application.QueueStatusContributor.QueueStatus;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotifyStuckQueuesTest {

    private final GetSystemStatusUseCase getSystemStatus = mock(GetSystemStatusUseCase.class);
    private final RoleRecipientQuery roleRecipientQuery = mock(RoleRecipientQuery.class);
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotifyStuckQueues notifyStuckQueues = new NotifyStuckQueues(
            getSystemStatus,
            roleRecipientQuery,
            repository,
            () -> Instant.parse("2026-08-13T10:00:00Z"),
            UUID::randomUUID);

    private final UUID platformAdminId = UUID.randomUUID();

    @Test
    void notifiesThePlatformAdminsOnceWhileTheProblemPersists() {
        givenPlatformAdmin();
        when(getSystemStatus.get()).thenReturn(status(3));

        assertThat(notifyStuckQueues.run()).isEqualTo(1);
        // Sin antirrepeticion el aviso saldria en cada pasada.
        assertThat(notifyStuckQueues.run()).isZero();
        assertThat(notifyStuckQueues.run()).isZero();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.type()).isEqualTo(NotificationType.SYSTEM_QUEUE_STUCK);
        assertThat(saved.tenantId()).isEqualTo(PlatformTenant.ID);
        assertThat(saved.recipientUserId()).isEqualTo(platformAdminId);
        assertThat(saved.body()).contains("3");
        assertThat(saved.actionPath()).isEqualTo("/platform/system-status");
    }

    @Test
    void notifiesAgainAfterTheProblemIsSolvedAndReappears() {
        givenPlatformAdmin();
        when(getSystemStatus.get()).thenReturn(status(1));
        assertThat(notifyStuckQueues.run()).isEqualTo(1);

        when(getSystemStatus.get()).thenReturn(status(0));
        assertThat(notifyStuckQueues.run()).isZero();

        when(getSystemStatus.get()).thenReturn(status(2));
        assertThat(notifyStuckQueues.run()).isEqualTo(1);
    }

    @Test
    void doesNotNotifyWhenNothingIsStuck() {
        when(getSystemStatus.get()).thenReturn(status(0));

        assertThat(notifyStuckQueues.run()).isZero();
        verify(repository, never()).save(any());
    }

    private void givenPlatformAdmin() {
        when(roleRecipientQuery.findActivePlatformAdmins())
                .thenReturn(List.of(new NotificationRecipient(platformAdminId, "plataforma@tfp.test")));
    }

    private static SystemStatus status(long failed) {
        return new SystemStatus(List.of(new QueueStatus("notifications", 0, failed)));
    }
}
