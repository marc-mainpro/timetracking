package com.tfp.timetracking.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T703: dos "instancias" del publicador (dos hilos llamando a {@code
 * publishBatch()} en paralelo, simulando dos nodos/schedulers) nunca
 * publican el mismo mensaje dos veces, gracias al {@code FOR UPDATE SKIP
 * LOCKED} de {@code claimBatch} (T701). Cada mensaje reclamado debe llegar
 * exactamente una vez al puerto {@link IntegrationEventPublisher}.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublishPendingOutboxMessagesConcurrencyIntegrationTest {

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
    }

    @Autowired
    private PublishPendingOutboxMessages publishPendingOutboxMessages;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @MockitoBean
    private IntegrationEventPublisher integrationEventPublisher;

    @Test
    void twoConcurrentPublisherRunsNeverPublishTheSameMessageTwice() throws Exception {
        ConcurrentHashMap<UUID, AtomicInteger> deliveries = new ConcurrentHashMap<>();
        org.mockito.Mockito.doAnswer(invocation -> {
                    IntegrationEvent event = invocation.getArgument(0);
                    deliveries.computeIfAbsent(event.eventId(), id -> new AtomicInteger()).incrementAndGet();
                    return null;
                })
                .when(integrationEventPublisher)
                .publish(org.mockito.ArgumentMatchers.any());

        int messageCount = 20;
        for (int i = 0; i < messageCount; i++) {
            outboxMessageRepository.save(newPendingMessage());
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> workerA = executor.submit(() -> {
                start.await();
                return publishPendingOutboxMessages.publishBatch();
            });
            Future<Integer> workerB = executor.submit(() -> {
                start.await();
                return publishPendingOutboxMessages.publishBatch();
            });
            start.countDown();

            int processedByA = workerA.get(20, TimeUnit.SECONDS);
            int processedByB = workerB.get(20, TimeUnit.SECONDS);

            assertThat(processedByA + processedByB).isEqualTo(messageCount);
            assertThat(deliveries).hasSize(messageCount);
            deliveries.values().forEach(count -> assertThat(count.get())
                    .as("cada mensaje debe entregarse exactamente una vez")
                    .isEqualTo(1));
        } finally {
            executor.shutdownNow();
        }
    }

    private static OutboxMessage newPendingMessage() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
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
