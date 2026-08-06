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
import com.tfp.timetracking.outbox.application.ProcessedEventStore;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationEventListenerTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final RecipientEmailQuery emailQuery = mock(RecipientEmailQuery.class);
    private final ProcessedEventStore processedEventStore = mock(ProcessedEventStore.class);
    private final NotificationEventListener listener = new NotificationEventListener(
            repository,
            emailQuery,
            processedEventStore,
            () -> Instant.parse("2026-08-06T10:00:00Z"),
            UUID::randomUUID);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @Test
    void createsANotificationForAnApprovedAbsence() {
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(emailQuery.findEmail(tenantId, employeeId)).thenReturn(Optional.of("empleado@acme.test"));

        listener.onEvent(event("absence.absence-approved.v1", Map.of("employeeId", employeeId)));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.type()).isEqualTo(NotificationType.ABSENCE_APPROVED);
        assertThat(saved.recipientUserId()).isEqualTo(employeeId);
        assertThat(saved.recipientEmail()).isEqualTo("empleado@acme.test");
        assertThat(saved.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void ignoresEventTypesItDoesNotNotify() {
        listener.onEvent(event("time-tracking.workday-started.v1", Map.of("employeeId", employeeId)));

        verify(repository, never()).save(any());
        verify(processedEventStore, never()).tryClaim(any(), any());
    }

    @Test
    void doesNotCreateADuplicateWhenTheEventIsRedelivered() {
        // La entrega es at-least-once y, desde que el publicador propaga los
        // fallos, el reintento reejecuta tambien a los consumidores que ya
        // habian terminado bien.
        when(processedEventStore.tryClaim(any(), any())).thenReturn(false);

        listener.onEvent(event("absence.absence-approved.v1", Map.of("employeeId", employeeId)));

        verify(repository, never()).save(any());
    }

    @Test
    void createsTheNotificationEvenWithoutARecipientEmail() {
        // Sin correo no hay envio, pero el aviso dentro de la aplicacion sigue
        // siendo util y no debe perderse.
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(emailQuery.findEmail(tenantId, employeeId)).thenReturn(Optional.empty());

        listener.onEvent(event("corrections.correction-rejected.v1", Map.of("employeeId", employeeId)));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().recipientEmail()).isNull();
        assertThat(captor.getValue().isDeliverable()).isFalse();
    }

    @Test
    void skipsEventsWithoutARecipientWithoutConsumingTheClaim() {
        listener.onEvent(event("corrections.correction-approved.v1", Map.of()));

        verify(repository, never()).save(any());
        verify(processedEventStore, never()).tryClaim(any(), any());
    }

    @Test
    void acceptsARecipientSerialisedAsText() {
        // El payload viaja como JSON en outbox_message, asi que al releerlo los
        // UUID llegan como cadenas y no como UUID.
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(emailQuery.findEmail(tenantId, employeeId)).thenReturn(Optional.of("empleado@acme.test"));

        listener.onEvent(event("corrections.correction-approved.v1", Map.of("employeeId", employeeId.toString())));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().recipientUserId()).isEqualTo(employeeId);
    }

    @Test
    void includesTheDetectedAnomaliesInTheBody() {
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(emailQuery.findEmail(tenantId, employeeId)).thenReturn(Optional.of("empleado@acme.test"));

        listener.onEvent(event(
                "time-tracking.workday-anomaly-detected.v1",
                Map.of("employeeId", employeeId, "anomalies", "REQUIRED_BREAK_NOT_MET")));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().body()).contains("REQUIRED_BREAK_NOT_MET");
    }

    private IntegrationEvent event(String eventType, Map<String, Object> payload) {
        return new IntegrationEvent(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.parse("2026-08-06T09:00:00Z"),
                tenantId,
                UUID.randomUUID(),
                "Aggregate",
                payload);
    }
}
