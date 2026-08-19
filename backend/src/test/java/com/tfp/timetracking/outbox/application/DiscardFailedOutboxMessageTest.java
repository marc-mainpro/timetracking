package com.tfp.timetracking.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
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
class DiscardFailedOutboxMessageTest {

    @Mock
    private OutboxMessageRepository repository;

    @Test
    void discardingAnUnknownMessageThrowsNotFound() {
        DiscardFailedOutboxMessage discard = new DiscardFailedOutboxMessage(repository);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> discard.discard(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void discardingAMessageThatIsNotFailedThrows() {
        // Descartar es renunciar a un trabajo agotado; sobre un mensaje que
        // todavia se esta intentando seria perder trabajo vivo.
        DiscardFailedOutboxMessage discard = new DiscardFailedOutboxMessage(repository);
        OutboxMessage pending = message(OutboxMessageStatus.PENDING);
        when(repository.findById(pending.id())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> discard.discard(pending.id())).isInstanceOf(OutboxMessageNotFailedException.class);
        verify(repository, never()).discardFailed(pending.id());
    }

    @Test
    void discardingAFailedMessageReturnsItsStateBeforeTheChange() {
        DiscardFailedOutboxMessage discard = new DiscardFailedOutboxMessage(repository);
        OutboxMessage failed = message(OutboxMessageStatus.FAILED);
        when(repository.findById(failed.id())).thenReturn(Optional.of(failed));
        when(repository.discardFailed(failed.id())).thenReturn(true);

        OutboxMessage discarded = discard.discard(failed.id());

        // La foto previa es lo que permite auditar la accion sin releer nada.
        assertThat(discarded.lastError()).isEqualTo("some error");
        assertThat(discarded.attempts()).isEqualTo(8);
        verify(repository).discardFailed(failed.id());
    }

    @Test
    void losingTheRaceAgainstAnotherAdminIsRejected() {
        DiscardFailedOutboxMessage discard = new DiscardFailedOutboxMessage(repository);
        OutboxMessage failed = message(OutboxMessageStatus.FAILED);
        UUID id = failed.id();
        when(repository.findById(id))
                .thenReturn(Optional.of(failed))
                .thenReturn(Optional.of(message(OutboxMessageStatus.PENDING)));
        when(repository.discardFailed(id)).thenReturn(false);

        assertThatThrownBy(() -> discard.discard(id)).isInstanceOf(OutboxMessageNotFailedException.class);
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
