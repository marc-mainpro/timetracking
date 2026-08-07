package com.tfp.timetracking.outbox.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.outbox.application.ProcessedEventStore;
import com.tfp.timetracking.outbox.infrastructure.demo.DemoIdempotentEventConsumer.ConsumptionResult;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * T704: pruebas unitarias (sin base de datos) del patron de idempotencia del
 * consumidor de demostracion: aplicar el evento la primera vez e ignorarlo si
 * la reserva del par {@code (eventId, consumidor)} ya la tenia otro.
 *
 * <p>La carrera entre dos hilos ya no necesita test propio aqui: la reserva es
 * una unica sentencia atomica en {@link ProcessedEventStore}, no un
 * comprobar-y-despues-insertar, asi que el caso de carrera es indistinguible
 * del duplicado y se cubre donde vive, en la prueba de integracion del store.
 */
class DemoIdempotentEventConsumerTest {

    @Test
    void processesAnEventSeenForTheFirstTime() {
        ProcessedEventStore store = mock(ProcessedEventStore.class);
        when(store.tryClaim(any(), eq(DemoIdempotentEventConsumer.CONSUMER))).thenReturn(true);
        DemoIdempotentEventConsumer consumer = new DemoIdempotentEventConsumer(store);

        ConsumptionResult result = consumer.consume(sampleEvent());

        assertThat(result).isEqualTo(ConsumptionResult.PROCESSED);
        assertThat(consumer.effectsAppliedCount()).isEqualTo(1);
    }

    @Test
    void ignoresAnEventAlreadyClaimedByThisConsumer() {
        ProcessedEventStore store = mock(ProcessedEventStore.class);
        when(store.tryClaim(any(), any())).thenReturn(false);
        DemoIdempotentEventConsumer consumer = new DemoIdempotentEventConsumer(store);

        ConsumptionResult result = consumer.consume(sampleEvent());

        assertThat(result).isEqualTo(ConsumptionResult.DUPLICATE_IGNORED);
        assertThat(consumer.effectsAppliedCount()).isZero();
    }

    @Test
    void claimsUnderItsOwnConsumerName() {
        // Si reservase con una clave compartida, el primer consumidor en marcar
        // el evento dejaria a los demas creyendo que ya estaba procesado.
        ProcessedEventStore store = mock(ProcessedEventStore.class);
        when(store.tryClaim(any(), eq(DemoIdempotentEventConsumer.CONSUMER))).thenReturn(true);
        DemoIdempotentEventConsumer consumer = new DemoIdempotentEventConsumer(store);

        assertThat(consumer.consume(sampleEvent())).isEqualTo(ConsumptionResult.PROCESSED);
    }

    @Test
    void onEventDelegatesToConsume() {
        ProcessedEventStore store = mock(ProcessedEventStore.class);
        when(store.tryClaim(any(), any())).thenReturn(true);
        DemoIdempotentEventConsumer consumer = new DemoIdempotentEventConsumer(store);

        consumer.onEvent(sampleEvent());

        assertThat(consumer.effectsAppliedCount()).isEqualTo(1);
    }

    private static IntegrationEvent sampleEvent() {
        UUID aggregateId = UUID.randomUUID();
        return new IntegrationEvent(
                UUID.randomUUID(),
                "time-tracking.workday-closed.v1",
                1,
                Instant.now(),
                UUID.randomUUID(),
                aggregateId,
                "Workday",
                Map.of("workdayId", aggregateId));
    }
}
