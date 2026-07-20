package com.tfp.timetracking.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import com.tfp.timetracking.shared.domain.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pruebas de integracion (Testcontainers, T703) del publicador de outbox
 * contra una base de datos real: publicacion feliz, backoff creciente,
 * agotamiento de intentos -&gt; FAILED, recuperacion de un mensaje PROCESSING
 * huerfano y reintento manual que vuelve a publicar.
 *
 * <p>Sin sleeps: el tiempo se controla explicitamente con {@link
 * MutableClock} (bean {@code @Primary} que sustituye a {@code SystemClock}
 * solo en este contexto de test), avanzando el reloj manualmente entre
 * llamadas a {@code publishBatch()} en vez de esperar a que el backoff
 * real transcurra.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublishPendingOutboxMessagesIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("timetracking")
            .withUsername("timetracking")
            .withPassword("timetracking");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("outbox.max-attempts", () -> "3");
    }

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        }
    }

    @Autowired
    private PublishPendingOutboxMessages publishPendingOutboxMessages;

    @Autowired
    private RetryFailedOutboxMessage retryFailedOutboxMessage;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Autowired
    private MutableClock clock;

    @MockitoBean
    private IntegrationEventPublisher integrationEventPublisher;

    @Test
    void successfulPublicationMarksMessagePublished() {
        OutboxMessage message = outboxMessageRepository.save(newPendingMessage());
        doNothing().when(integrationEventPublisher).publish(any());

        int processed = publishPendingOutboxMessages.publishBatch();

        assertThat(processed).isEqualTo(1);
        OutboxMessage reloaded = outboxMessageRepository.findById(message.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(OutboxMessageStatus.PUBLISHED);
        assertThat(reloaded.publishedAt()).isNotNull();
    }

    @Test
    void repeatedFailuresGrowTheBackoffThenFail() {
        OutboxMessage message = outboxMessageRepository.save(newPendingMessage());
        doThrow(new RuntimeException("temporary failure")).when(integrationEventPublisher).publish(any());

        // Intento 1: falla, se programa un reintento (maxAttempts=3 en este test).
        Instant t0 = clock.now();
        assertThat(publishPendingOutboxMessages.publishBatch()).isEqualTo(1);
        OutboxMessage afterFirstFailure = outboxMessageRepository.findById(message.id()).orElseThrow();
        assertThat(afterFirstFailure.status()).isEqualTo(OutboxMessageStatus.PENDING);
        assertThat(afterFirstFailure.attempts()).isEqualTo(1);
        assertThat(afterFirstFailure.nextAttemptAt()).isAfter(t0);
        Instant firstNextAttemptAt = afterFirstFailure.nextAttemptAt();
        Duration firstDelay = Duration.between(t0, firstNextAttemptAt);

        // Antes de que venza el backoff: no hay nada que reclamar.
        assertThat(publishPendingOutboxMessages.publishBatch()).isZero();

        // Avanzamos el reloj (sin sleeps) hasta despues del primer backoff.
        clock.setInstant(firstNextAttemptAt.plusSeconds(1));
        Instant t1 = clock.now();

        // Intento 2: vuelve a fallar, backoff mayor que el anterior.
        assertThat(publishPendingOutboxMessages.publishBatch()).isEqualTo(1);
        OutboxMessage afterSecondFailure = outboxMessageRepository.findById(message.id()).orElseThrow();
        assertThat(afterSecondFailure.attempts()).isEqualTo(2);
        assertThat(afterSecondFailure.status()).isEqualTo(OutboxMessageStatus.PENDING);
        Duration secondDelay = Duration.between(t1, afterSecondFailure.nextAttemptAt());
        assertThat(secondDelay).isGreaterThan(firstDelay);

        clock.setInstant(afterSecondFailure.nextAttemptAt().plusSeconds(1));

        // Intento 3 (maxAttempts=3): agota los intentos -> FAILED.
        assertThat(publishPendingOutboxMessages.publishBatch()).isEqualTo(1);
        OutboxMessage afterThirdFailure = outboxMessageRepository.findById(message.id()).orElseThrow();
        assertThat(afterThirdFailure.status()).isEqualTo(OutboxMessageStatus.FAILED);
        assertThat(afterThirdFailure.attempts()).isEqualTo(3);
        assertThat(afterThirdFailure.lastError()).contains("temporary failure");
    }

    @Test
    void manualRetryAfterExhaustionRepublishesTheMessage() {
        OutboxMessage message = outboxMessageRepository.save(newPendingMessage());
        doThrow(new RuntimeException("boom")).when(integrationEventPublisher).publish(any());

        publishPendingOutboxMessages.publishBatch();
        clock.advanceBy(Duration.ofMinutes(5));
        publishPendingOutboxMessages.publishBatch();
        clock.advanceBy(Duration.ofMinutes(10));
        publishPendingOutboxMessages.publishBatch();

        OutboxMessage failedMessage = outboxMessageRepository.findById(message.id()).orElseThrow();
        assertThat(failedMessage.status()).isEqualTo(OutboxMessageStatus.FAILED);

        reset(integrationEventPublisher);
        doNothing().when(integrationEventPublisher).publish(any());

        retryFailedOutboxMessage.retry(message.id());
        OutboxMessage resetMessage = outboxMessageRepository.findById(message.id()).orElseThrow();
        assertThat(resetMessage.status()).isEqualTo(OutboxMessageStatus.PENDING);
        assertThat(resetMessage.attempts()).isZero();

        int processed = publishPendingOutboxMessages.publishBatch();

        assertThat(processed).isEqualTo(1);
        OutboxMessage republished = outboxMessageRepository.findById(message.id()).orElseThrow();
        assertThat(republished.status()).isEqualTo(OutboxMessageStatus.PUBLISHED);
    }

    @Test
    void orphanedProcessingMessageIsReclaimedAndPublished() {
        // Simula un worker que reclamo el mensaje (PROCESSING) y murio antes
        // de marcarlo PUBLISHED/reintento/FAILED: el lease (nextAttemptAt)
        // ya vencio.
        Instant now = clock.now();
        OutboxMessage orphan = outboxMessageRepository.save(new OutboxMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Workday",
                UUID.randomUUID(),
                "time-tracking.workday-closed.v1",
                1,
                Map.of("foo", "bar"),
                now,
                null,
                0,
                now.minusSeconds(1),
                null,
                OutboxMessageStatus.PROCESSING,
                now));
        doNothing().when(integrationEventPublisher).publish(any());

        int processed = publishPendingOutboxMessages.publishBatch();

        assertThat(processed).isEqualTo(1);
        OutboxMessage reloaded = outboxMessageRepository.findById(orphan.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(OutboxMessageStatus.PUBLISHED);
    }

    private OutboxMessage newPendingMessage() {
        Instant now = clock.now().truncatedTo(ChronoUnit.MICROS);
        return new OutboxMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Workday",
                UUID.randomUUID(),
                "time-tracking.workday-closed.v1",
                1,
                Map.of("foo", "bar"),
                now,
                null,
                0,
                null,
                null,
                OutboxMessageStatus.PENDING,
                now);
    }
}
