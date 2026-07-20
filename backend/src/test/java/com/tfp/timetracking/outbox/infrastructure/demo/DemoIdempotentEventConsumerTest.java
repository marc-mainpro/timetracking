package com.tfp.timetracking.outbox.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.outbox.infrastructure.demo.DemoIdempotentEventConsumer.ConsumptionResult;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * T704: pruebas unitarias (sin base de datos) del patron de idempotencia del
 * consumidor de demostracion: aplicar el evento la primera vez, ignorarlo si
 * {@code eventId} ya se proceso, e ignorarlo tambien si dos "consumidores"
 * pierden una carrera por insertar la misma fila {@code processed_event}
 * (violacion de la clave primaria).
 */
class DemoIdempotentEventConsumerTest {

    @Test
    void processesAndRecordsAnEventSeenForTheFirstTime() {
        ProcessedEventJpaRepository repository = mock(ProcessedEventJpaRepository.class);
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-07-20T10:00:00Z");
        when(clock.now()).thenReturn(now);
        when(repository.existsById(any())).thenReturn(false);
        DemoIdempotentEventConsumer consumer = new DemoIdempotentEventConsumer(repository, clock);
        IntegrationEvent event = sampleEvent();

        ConsumptionResult result = consumer.consume(event);

        assertThat(result).isEqualTo(ConsumptionResult.PROCESSED);
        assertThat(consumer.effectsAppliedCount()).isEqualTo(1);
        verify(repository).save(any(ProcessedEventJpaEntity.class));
    }

    @Test
    void ignoresAnEventAlreadyMarkedAsProcessed() {
        ProcessedEventJpaRepository repository = mock(ProcessedEventJpaRepository.class);
        Clock clock = mock(Clock.class);
        when(repository.existsById(any())).thenReturn(true);
        DemoIdempotentEventConsumer consumer = new DemoIdempotentEventConsumer(repository, clock);
        IntegrationEvent event = sampleEvent();

        ConsumptionResult result = consumer.consume(event);

        assertThat(result).isEqualTo(ConsumptionResult.DUPLICATE_IGNORED);
        assertThat(consumer.effectsAppliedCount()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void treatsAPrimaryKeyRaceAsADuplicateInsteadOfPropagatingTheError() {
        ProcessedEventJpaRepository repository = mock(ProcessedEventJpaRepository.class);
        Clock clock = mock(Clock.class);
        when(clock.now()).thenReturn(Instant.now());
        when(repository.existsById(any())).thenReturn(false);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        DemoIdempotentEventConsumer consumer = new DemoIdempotentEventConsumer(repository, clock);

        ConsumptionResult result = consumer.consume(sampleEvent());

        assertThat(result).isEqualTo(ConsumptionResult.DUPLICATE_IGNORED);
        assertThat(consumer.effectsAppliedCount()).isZero();
    }

    @Test
    void onEventDelegatesToConsume() {
        ProcessedEventJpaRepository repository = mock(ProcessedEventJpaRepository.class);
        Clock clock = mock(Clock.class);
        when(clock.now()).thenReturn(Instant.now());
        when(repository.existsById(any())).thenReturn(false);
        DemoIdempotentEventConsumer consumer = new DemoIdempotentEventConsumer(repository, clock);

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
