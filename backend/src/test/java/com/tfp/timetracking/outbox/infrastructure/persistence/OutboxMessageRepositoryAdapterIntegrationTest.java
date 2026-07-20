package com.tfp.timetracking.outbox.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OutboxMessageRepositoryAdapterIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Test
    void savePersistsPendingMessageWithPayload() {
        OutboxMessage saved = outboxMessageRepository.save(newPendingMessage());

        assertThat(saved.status()).isEqualTo(OutboxMessageStatus.PENDING);
        assertThat(saved.payload()).containsEntry("foo", "bar");

        String status = jdbcTemplate.queryForObject(
                "select status from outbox_message where id = ?::uuid", String.class, saved.id().toString());
        assertThat(status).isEqualTo("PENDING");
    }

    @Test
    void claimBatchMarksProcessingAndSetsLease() {
        OutboxMessage saved = outboxMessageRepository.save(newPendingMessage());
        Instant now = Instant.now();
        Instant lease = now.plusSeconds(30);

        List<OutboxMessage> claimed = outboxMessageRepository.claimBatch(10, now, lease);

        assertThat(claimed).extracting(OutboxMessage::id).contains(saved.id());
        OutboxMessage claimedMessage =
                claimed.stream().filter(m -> m.id().equals(saved.id())).findFirst().orElseThrow();
        assertThat(claimedMessage.status()).isEqualTo(OutboxMessageStatus.PROCESSING);
        assertThat(claimedMessage.nextAttemptAt()).isCloseTo(lease, within(1, ChronoUnit.SECONDS));

        String status = jdbcTemplate.queryForObject(
                "select status from outbox_message where id = ?::uuid", String.class, saved.id().toString());
        assertThat(status).isEqualTo("PROCESSING");
    }

    @Test
    void claimBatchDoesNotReturnFuturePendingMessages() {
        OutboxMessage future = outboxMessageRepository.save(newPendingMessageWithNextAttemptAt(Instant.now().plusSeconds(3600)));

        List<OutboxMessage> claimed = outboxMessageRepository.claimBatch(10, Instant.now(), Instant.now().plusSeconds(30));

        assertThat(claimed).extracting(OutboxMessage::id).doesNotContain(future.id());
    }

    @Test
    void markPublishedTransitionsFromProcessingToPublished() {
        OutboxMessage saved = outboxMessageRepository.save(newPendingMessage());
        outboxMessageRepository.claimBatch(10, Instant.now(), Instant.now().plusSeconds(30));

        Instant publishedAt = Instant.now();
        outboxMessageRepository.markPublished(saved.id(), publishedAt);

        String status = jdbcTemplate.queryForObject(
                "select status from outbox_message where id = ?::uuid", String.class, saved.id().toString());
        assertThat(status).isEqualTo("PUBLISHED");
        Instant persistedPublishedAt = jdbcTemplate.queryForObject(
                "select published_at from outbox_message where id = ?::uuid",
                Instant.class,
                saved.id().toString());
        assertThat(persistedPublishedAt).isCloseTo(publishedAt, within(1, ChronoUnit.SECONDS));
    }

    @Test
    void markRetryReturnsMessageToPendingWithBackoff() {
        OutboxMessage saved = outboxMessageRepository.save(newPendingMessage());
        outboxMessageRepository.claimBatch(10, Instant.now(), Instant.now().plusSeconds(30));

        Instant nextAttemptAt = Instant.now().plusSeconds(120).truncatedTo(ChronoUnit.MICROS);
        outboxMessageRepository.markRetry(saved.id(), 1, nextAttemptAt, "boom");

        String status = jdbcTemplate.queryForObject(
                "select status from outbox_message where id = ?::uuid", String.class, saved.id().toString());
        assertThat(status).isEqualTo("PENDING");
        Integer attempts = jdbcTemplate.queryForObject(
                "select attempts from outbox_message where id = ?::uuid", Integer.class, saved.id().toString());
        assertThat(attempts).isEqualTo(1);
        String lastError = jdbcTemplate.queryForObject(
                "select last_error from outbox_message where id = ?::uuid", String.class, saved.id().toString());
        assertThat(lastError).isEqualTo("boom");

        // Should not be claimable again until nextAttemptAt.
        List<OutboxMessage> claimed =
                outboxMessageRepository.claimBatch(10, Instant.now(), Instant.now().plusSeconds(30));
        assertThat(claimed).extracting(OutboxMessage::id).doesNotContain(saved.id());
    }

    @Test
    void markFailedTransitionsToFailedTerminalState() {
        OutboxMessage saved = outboxMessageRepository.save(newPendingMessage());
        outboxMessageRepository.claimBatch(10, Instant.now(), Instant.now().plusSeconds(30));

        outboxMessageRepository.markFailed(saved.id(), "unrecoverable");

        String status = jdbcTemplate.queryForObject(
                "select status from outbox_message where id = ?::uuid", String.class, saved.id().toString());
        assertThat(status).isEqualTo("FAILED");

        List<OutboxMessage> claimed =
                outboxMessageRepository.claimBatch(10, Instant.now(), Instant.now().plusSeconds(30));
        assertThat(claimed).extracting(OutboxMessage::id).doesNotContain(saved.id());
    }

    @Test
    void archivePublishedBeforeDeletesOnlyOldPublishedMessages() {
        OutboxMessage recent = outboxMessageRepository.save(newPendingMessage());
        outboxMessageRepository.markFailed(recent.id(), "n/a");
        OutboxMessage toArchive = outboxMessageRepository.save(newPendingMessage());
        outboxMessageRepository.markPublished(toArchive.id(), Instant.now().minus(60, ChronoUnit.DAYS));

        int archived = outboxMessageRepository.archivePublishedBefore(Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(archived).isEqualTo(1);
        Integer remainingCount = jdbcTemplate.queryForObject(
                "select count(*) from outbox_message where id = ?::uuid", Integer.class, toArchive.id().toString());
        assertThat(remainingCount).isZero();
        Integer keptCount = jdbcTemplate.queryForObject(
                "select count(*) from outbox_message where id = ?::uuid", Integer.class, recent.id().toString());
        assertThat(keptCount).isEqualTo(1);
    }

    private static OutboxMessage newPendingMessage() {
        return newPendingMessageWithNextAttemptAt(null);
    }

    private static OutboxMessage newPendingMessageWithNextAttemptAt(Instant nextAttemptAt) {
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
                nextAttemptAt,
                null,
                OutboxMessageStatus.PENDING,
                now);
    }
}
