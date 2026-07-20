package com.tfp.timetracking.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T703: el job de archivado borra unicamente los mensajes {@code PUBLISHED}
 * mas antiguos que {@code outbox.archive-retention} y respeta el resto
 * (PUBLISHED recientes, PENDING, PROCESSING, FAILED), delegando en {@code
 * OutboxMessageRepository#archivePublishedBefore} (T701).
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArchivePublishedOutboxMessagesIntegrationTest {

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
        registry.add("outbox.archive-retention", () -> "P30D");
    }

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-07-20T00:00:00Z"));
        }
    }

    @Autowired
    private ArchivePublishedOutboxMessages archivePublishedOutboxMessages;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Autowired
    private MutableClock clock;

    @Test
    void archivesOnlyOldPublishedMessages() {
        Instant now = clock.now();

        OutboxMessage oldPublished = outboxMessageRepository.save(
                message(OutboxMessageStatus.PUBLISHED, now.minus(Duration.ofDays(40)), now.minus(Duration.ofDays(45))));
        OutboxMessage recentPublished = outboxMessageRepository.save(
                message(OutboxMessageStatus.PUBLISHED, now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(2))));
        OutboxMessage pending = outboxMessageRepository.save(message(OutboxMessageStatus.PENDING, null, now));
        OutboxMessage processing = outboxMessageRepository.save(message(OutboxMessageStatus.PROCESSING, null, now));
        OutboxMessage failed = outboxMessageRepository.save(message(OutboxMessageStatus.FAILED, null, now.minus(Duration.ofDays(90))));

        int archived = archivePublishedOutboxMessages.archive();

        assertThat(archived).isEqualTo(1);
        assertThat(outboxMessageRepository.findById(oldPublished.id())).isEmpty();
        assertThat(outboxMessageRepository.findById(recentPublished.id())).isPresent();
        assertThat(outboxMessageRepository.findById(pending.id())).isPresent();
        assertThat(outboxMessageRepository.findById(processing.id())).isPresent();
        assertThat(outboxMessageRepository.findById(failed.id())).isPresent();
    }

    private static OutboxMessage message(OutboxMessageStatus status, Instant publishedAt, Instant createdAt) {
        Instant occurredAt = createdAt.truncatedTo(ChronoUnit.MICROS);
        return new OutboxMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Workday",
                UUID.randomUUID(),
                "time-tracking.workday-closed.v1",
                1,
                Map.of("foo", "bar"),
                occurredAt,
                publishedAt == null ? null : publishedAt.truncatedTo(ChronoUnit.MICROS),
                0,
                null,
                null,
                status,
                occurredAt);
    }
}
