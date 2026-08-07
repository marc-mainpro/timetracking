package com.tfp.timetracking.outbox.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.outbox.application.ProcessedEventStore;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * La deduplicacion es por {@code (eventId, consumidor)} y la reserva es atomica
 * (V23, RF-OUT-005).
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class JdbcProcessedEventStoreIntegrationTest {

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
    private ProcessedEventStore store;

    @Test
    void grantsTheClaimOnlyOnceToTheSameConsumer() {
        UUID eventId = UUID.randomUUID();

        assertThat(store.tryClaim(eventId, "consumer-a")).isTrue();
        assertThat(store.tryClaim(eventId, "consumer-a")).isFalse();
    }

    @Test
    void eachConsumerKeepsItsOwnClaimForTheSameEvent() {
        // Con una clave por evento, el primero en marcarlo dejaria al segundo
        // creyendo que ya lo habia procesado, y su efecto no se aplicaria nunca.
        UUID eventId = UUID.randomUUID();

        assertThat(store.tryClaim(eventId, "consumer-a")).isTrue();
        assertThat(store.tryClaim(eventId, "consumer-b")).isTrue();
    }

    @Test
    void onlyOneOfTwoConcurrentClaimsSucceeds() throws Exception {
        UUID eventId = UUID.randomUUID();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> attempts = java.util.Collections.nCopies(
                    threads, () -> store.tryClaim(eventId, "concurrent-consumer"));

            List<Boolean> results = pool.invokeAll(attempts).stream()
                    .map(JdbcProcessedEventStoreIntegrationTest::get)
                    .collect(Collectors.toList());

            assertThat(results).containsOnlyOnce(true);
        } finally {
            pool.shutdownNow();
        }
    }

    private static Boolean get(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
