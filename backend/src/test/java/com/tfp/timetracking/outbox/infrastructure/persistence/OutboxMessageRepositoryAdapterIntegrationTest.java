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

        outboxMessageRepository.markFailed(saved.id(), 5, "unrecoverable");

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
        outboxMessageRepository.markFailed(recent.id(), 1, "n/a");
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

    @Test
    void requeueAndDiscardOnlyTouchFailedMessages() {
        OutboxMessage claimed = outboxMessageRepository.save(newPendingMessage());
        outboxMessageRepository.markRetry(claimed.id(), 1, null, "boom");
        jdbcTemplate.update(
                "update outbox_message set status = 'PROCESSING' where id = ?::uuid",
                claimed.id().toString());

        // Es la carrera que importa: el publicador ya reclamo el mensaje, asi
        // que reencolarlo ahora lo publicaria dos veces.
        assertThat(outboxMessageRepository.requeueFailed(claimed.id())).isFalse();
        assertThat(outboxMessageRepository.discardFailed(claimed.id())).isFalse();
        assertThat(statusOf(claimed)).isEqualTo("PROCESSING");
    }

    @Test
    void requeueingAFailedMessageResetsItsAttempts() {
        OutboxMessage failed = outboxMessageRepository.save(newPendingMessage());
        outboxMessageRepository.markFailed(failed.id(), 8, "SMTP caido");

        assertThat(outboxMessageRepository.requeueFailed(failed.id())).isTrue();

        OutboxMessage requeued = outboxMessageRepository.findById(failed.id()).orElseThrow();
        assertThat(requeued.status()).isEqualTo(OutboxMessageStatus.PENDING);
        assertThat(requeued.attempts()).isZero();
        assertThat(requeued.lastError()).isNull();
        assertThat(requeued.nextAttemptAt()).isNull();
    }

    @Test
    void discardingAFailedMessageKeepsItsError() {
        // Cuenta el total de fallidos, asi que parte de una tabla limpia: los
        // demas tests de la clase dejan mensajes en otros estados.
        jdbcTemplate.update("delete from outbox_message");
        OutboxMessage failed = outboxMessageRepository.save(newPendingMessage());
        outboxMessageRepository.markFailed(failed.id(), 8, "SMTP caido");

        assertThat(outboxMessageRepository.discardFailed(failed.id())).isTrue();

        OutboxMessage discarded = outboxMessageRepository.findById(failed.id()).orElseThrow();
        assertThat(discarded.status()).isEqualTo(OutboxMessageStatus.DISCARDED);
        // La fila sobrevive con su error: es la traza que justifica conservarla.
        assertThat(discarded.lastError()).isEqualTo("SMTP caido");
        // Y deja de contar como incidencia que reclama atencion.
        assertThat(outboxMessageRepository.countFailed()).isZero();
    }

    @Test
    void failedMessagesAreListedOldestFirstAndPaginated() {
        jdbcTemplate.update("delete from outbox_message");
        OutboxMessage older = outboxMessageRepository.save(newPendingMessage());
        OutboxMessage newer = outboxMessageRepository.save(newPendingMessage());
        jdbcTemplate.update(
                "update outbox_message set created_at = NOW() - interval '1 day' where id = ?::uuid",
                older.id().toString());
        outboxMessageRepository.markFailed(older.id(), 8, "primero");
        outboxMessageRepository.markFailed(newer.id(), 8, "segundo");

        var firstPage = outboxMessageRepository.findByStatus(OutboxMessageStatus.FAILED, 0, 1);

        // Lo mas antiguo primero: es lo que lleva mas tiempo sin atenderse.
        assertThat(firstPage.content()).extracting(OutboxMessage::id).containsExactly(older.id());
        assertThat(firstPage.totalElements()).isEqualTo(2);
        assertThat(firstPage.totalPages()).isEqualTo(2);
    }

    private String statusOf(OutboxMessage message) {
        return jdbcTemplate.queryForObject(
                "select status from outbox_message where id = ?::uuid",
                String.class,
                message.id().toString());
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
