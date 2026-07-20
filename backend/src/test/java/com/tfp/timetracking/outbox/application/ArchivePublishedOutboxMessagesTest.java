package com.tfp.timetracking.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.shared.domain.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArchivePublishedOutboxMessagesTest {

    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");

    @Mock
    private OutboxMessageRepository repository;

    @Test
    void archivesUsingConfiguredRetentionCutoff() {
        Clock clock = () -> NOW;
        OutboxProperties properties = new OutboxProperties(
                Duration.ofSeconds(5), 50, 8, Duration.ofMinutes(5), Duration.ofDays(30), "0 0 3 * * *", true);
        Instant expectedCutoff = NOW.minus(Duration.ofDays(30));
        when(repository.archivePublishedBefore(expectedCutoff)).thenReturn(7);

        ArchivePublishedOutboxMessages useCase = new ArchivePublishedOutboxMessages(repository, clock, properties);

        int archived = useCase.archive();

        assertThat(archived).isEqualTo(7);
        verify(repository).archivePublishedBefore(eq(expectedCutoff));
    }
}
