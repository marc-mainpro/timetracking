package com.tfp.timetracking.outbox.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageNotFailedException;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetryFailedOutboxMessageTest {

    @Mock
    private OutboxMessageRepository repository;

    @Test
    void retryingAnUnknownMessageThrowsNotFound() {
        RetryFailedOutboxMessage retry = new RetryFailedOutboxMessage(repository);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> retry.retry(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void retryingAMessageThatIsNotFailedThrows() {
        RetryFailedOutboxMessage retry = new RetryFailedOutboxMessage(repository);
        OutboxMessage pending = message(OutboxMessageStatus.PENDING);
        when(repository.findById(pending.id())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> retry.retry(pending.id())).isInstanceOf(OutboxMessageNotFailedException.class);
    }

    @Test
    void retryingAFailedMessageResetsItToPending() {
        RetryFailedOutboxMessage retry = new RetryFailedOutboxMessage(repository);
        OutboxMessage failed = message(OutboxMessageStatus.FAILED);
        when(repository.findById(failed.id())).thenReturn(Optional.of(failed));

        retry.retry(failed.id());

        verify(repository).markRetry(eq(failed.id()), eq(0), isNull(), isNull());
    }

    private static OutboxMessage message(OutboxMessageStatus status) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
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
                8,
                null,
                "some error",
                status,
                now);
    }
}
