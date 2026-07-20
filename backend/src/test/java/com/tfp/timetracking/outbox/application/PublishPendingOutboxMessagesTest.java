package com.tfp.timetracking.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import com.tfp.timetracking.shared.domain.Clock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Pruebas unitarias (sin base de datos) de la logica de decision de {@link
 * PublishPendingOutboxMessages}: exito -> PUBLISHED, fallo con intentos
 * restantes -> retry con backoff, fallo con intentos agotados -> FAILED.
 * Las garantias de concurrencia/persistencia real (SKIP LOCKED, recuperacion
 * de huerfanos, archivado) se prueban en los tests de integracion con
 * Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
class PublishPendingOutboxMessagesTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private OutboxMessageRepository repository;

    @Mock
    private IntegrationEventPublisher publisher;

    private PublishPendingOutboxMessages useCase;

    @BeforeEach
    void setUp() {
        Clock clock = () -> NOW;
        OutboxProperties properties = new OutboxProperties(
                Duration.ofSeconds(5), 50, 3, Duration.ofMinutes(5), Duration.ofDays(30), "0 0 3 * * *", true);
        OutboxMetrics metrics = new OutboxMetrics(new SimpleMeterRegistry(), repository);
        useCase = new PublishPendingOutboxMessages(repository, publisher, clock, properties, metrics);
    }

    @Test
    void successfulPublicationMarksMessagePublished() {
        OutboxMessage message = pendingMessage(0);
        when(repository.claimBatch(eq(50), eq(NOW), eq(NOW.plus(Duration.ofMinutes(5)))))
                .thenReturn(List.of(message));

        int processed = useCase.publishBatch();

        assertThat(processed).isEqualTo(1);
        verify(publisher).publish(any());
        verify(repository).markPublished(message.id(), NOW);
        verify(repository, never()).markRetry(any(), anyInt(), any(), anyString());
        verify(repository, never()).markFailed(any(), anyInt(), anyString());
    }

    @Test
    void failureBelowMaxAttemptsSchedulesRetryWithGrowingBackoff() {
        OutboxMessage message = pendingMessage(0);
        when(repository.claimBatch(anyInt(), any(), any())).thenReturn(List.of(message));
        doThrow(new RuntimeException("boom")).when(publisher).publish(any());

        useCase.publishBatch();

        ArgumentCaptor<Instant> nextAttemptAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).markRetry(eq(message.id()), eq(1), nextAttemptAtCaptor.capture(), eq("boom"));
        verify(repository, never()).markFailed(any(), anyInt(), anyString());
        assertThat(nextAttemptAtCaptor.getValue()).isAfterOrEqualTo(NOW.plus(Duration.ofMinutes(1)));
    }

    @Test
    void failureAtMaxAttemptsMarksMessageFailed() {
        // maxAttempts=3: el mensaje ya tenia 2 intentos previos, este es el 3o.
        OutboxMessage message = pendingMessage(2);
        when(repository.claimBatch(anyInt(), any(), any())).thenReturn(List.of(message));
        doThrow(new RuntimeException("still failing")).when(publisher).publish(any());

        useCase.publishBatch();

        verify(repository).markFailed(message.id(), 3, "still failing");
        verify(repository, never()).markRetry(any(), anyInt(), any(), anyString());
    }

    @Test
    void emptyBatchDoesNothing() {
        when(repository.claimBatch(anyInt(), any(), any())).thenReturn(List.of());

        int processed = useCase.publishBatch();

        assertThat(processed).isZero();
        verify(publisher, never()).publish(any());
    }

    private static OutboxMessage pendingMessage(int attempts) {
        return new OutboxMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Workday",
                UUID.randomUUID(),
                "time-tracking.workday-closed.v1",
                1,
                Map.of("foo", "bar"),
                NOW,
                null,
                attempts,
                null,
                null,
                OutboxMessageStatus.PROCESSING,
                NOW);
    }
}
