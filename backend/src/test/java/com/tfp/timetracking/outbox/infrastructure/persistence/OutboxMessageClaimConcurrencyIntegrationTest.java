package com.tfp.timetracking.outbox.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba critica de T701: dos "workers" reclamando mensajes en paralelo
 * jamas reclaman el mismo mensaje, gracias a {@code FOR UPDATE SKIP LOCKED}.
 *
 * <p>No se usan sleeps arbitrarios (mismo criterio que
 * {@code concurrency.ConcurrencyIntegrationTest} de T604): el worker A abre
 * una transaccion real, reclama y se bloquea en un {@link CountDownLatch}
 * ANTES de hacer commit, para garantizar que sus filas siguen bloqueadas
 * cuando el worker B reclama concurrentemente.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OutboxMessageClaimConcurrencyIntegrationTest {

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
    private OutboxMessageRepository outboxMessageRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentWorkersNeverClaimTheSameMessage() throws Exception {
        List<UUID> messageIds = List.of(
                outboxMessageRepository.save(newPendingMessage()).id(),
                outboxMessageRepository.save(newPendingMessage()).id(),
                outboxMessageRepository.save(newPendingMessage()).id());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch workerAClaimed = new CountDownLatch(1);
        CountDownLatch releaseWorkerA = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<UUID>> workerA = executor.submit(() -> transactionTemplate.execute(status -> {
                List<UUID> claimed = claim(2);
                workerAClaimed.countDown();
                awaitUninterruptibly(releaseWorkerA);
                return claimed;
            }));

            assertThat(workerAClaimed.await(10, TimeUnit.SECONDS)).isTrue();

            Future<List<UUID>> workerB = executor.submit(() -> transactionTemplate.execute(status -> claim(2)));
            List<UUID> workerBClaimed = workerB.get(10, TimeUnit.SECONDS);

            releaseWorkerA.countDown();
            List<UUID> workerAClaimedIds = workerA.get(10, TimeUnit.SECONDS);

            assertThat(workerAClaimedIds).hasSize(2);
            assertThat(workerBClaimed).hasSize(1);
            assertThat(workerAClaimedIds)
                    .as("un mensaje reclamado por A jamas debe ser reclamado tambien por B")
                    .doesNotContainAnyElementsOf(workerBClaimed);
            assertThat(new java.util.HashSet<>(workerBClaimed))
                    .as("el conjunto reclamado por B debe ser el mensaje restante no bloqueado por A")
                    .isSubsetOf(new java.util.HashSet<>(messageIds));

            Integer processingCount =
                    jdbcTemplate.queryForObject("select count(*) from outbox_message where status = 'PROCESSING'", Integer.class);
            assertThat(processingCount).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }

    private List<UUID> claim(int limit) {
        Instant now = Instant.now();
        return outboxMessageRepository.claimBatch(limit, now, now.plusSeconds(30)).stream()
                .map(OutboxMessage::id)
                .toList();
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    latch.await();
                    return;
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
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
