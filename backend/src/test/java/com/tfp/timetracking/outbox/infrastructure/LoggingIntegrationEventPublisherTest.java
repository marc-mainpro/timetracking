package com.tfp.timetracking.outbox.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tfp.timetracking.outbox.application.IntegrationEventDeliveryException;
import com.tfp.timetracking.outbox.application.IntegrationEventListener;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * T704: {@link LoggingIntegrationEventPublisher} sigue siendo la unica
 * implementacion MVP de {@code IntegrationEventPublisher} (ADR-0005), pero
 * ahora tambien notifica a los {@link IntegrationEventListener} registrados
 * (en el MVP, el consumidor de demostracion idempotente). Estas pruebas
 * cubren ese fan-out de forma aislada, sin necesidad de una base de datos.
 */
class LoggingIntegrationEventPublisherTest {

    @Test
    void notifiesAllRegisteredListeners() {
        IntegrationEventListener first = mock(IntegrationEventListener.class);
        IntegrationEventListener second = mock(IntegrationEventListener.class);
        LoggingIntegrationEventPublisher publisher =
                new LoggingIntegrationEventPublisher(List.of(first, second));
        IntegrationEvent event = sampleEvent();

        publisher.publish(event);

        verify(first).onEvent(event);
        verify(second).onEvent(event);
    }

    @Test
    void aFailingListenerDoesNotPreventTheOthersFromRunning() {
        // El fallo se propaga, pero solo despues de intentarlo con todos: si se
        // cortase en el primero, quien recibe el evento dependeria del orden de
        // registro de los beans.
        IntegrationEventListener failing = mock(IntegrationEventListener.class);
        IntegrationEventListener healthy = mock(IntegrationEventListener.class);
        doThrow(new RuntimeException("listener boom")).when(failing).onEvent(any());
        LoggingIntegrationEventPublisher publisher =
                new LoggingIntegrationEventPublisher(List.of(failing, healthy));
        IntegrationEvent event = sampleEvent();

        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(IntegrationEventDeliveryException.class);

        verify(healthy).onEvent(event);
    }

    @Test
    void propagatesListenerFailureSoTheMessageIsRetried() {
        // Si esta excepcion no sale, PublishPendingOutboxMessages marca el
        // mensaje PUBLISHED y el correo se pierde en silencio (ADR-0012).
        IntegrationEventListener failing = mock(IntegrationEventListener.class);
        RuntimeException cause = new RuntimeException("SMTP caido");
        doThrow(cause).when(failing).onEvent(any());
        LoggingIntegrationEventPublisher publisher = new LoggingIntegrationEventPublisher(List.of(failing));
        IntegrationEvent event = sampleEvent();

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(IntegrationEventDeliveryException.class)
                .hasMessageContaining(event.eventType())
                .hasMessageContaining("IntegrationEventListener")
                .hasCause(cause);
    }

    @Test
    void publishesWithoutErrorWhenThereAreNoListeners() {
        LoggingIntegrationEventPublisher publisher = new LoggingIntegrationEventPublisher(List.of());

        publisher.publish(sampleEvent());
        // No excepcion: publicar sin listeners registrados es el caso normal fuera de tests.
    }

    @Test
    void doesNotNotifyListenersBeforePublishIsCalled() {
        IntegrationEventListener listener = mock(IntegrationEventListener.class);
        new LoggingIntegrationEventPublisher(List.of(listener));

        verifyNoInteractions(listener);
    }

    private static IntegrationEvent sampleEvent() {
        UUID tenantId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        return new IntegrationEvent(
                UUID.randomUUID(),
                "time-tracking.workday-closed.v1",
                1,
                Instant.now(),
                tenantId,
                aggregateId,
                "Workday",
                Map.of("workdayId", aggregateId));
    }
}
