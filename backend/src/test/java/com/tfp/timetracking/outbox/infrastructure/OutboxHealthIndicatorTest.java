package com.tfp.timetracking.outbox.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.outbox.application.OutboxProperties;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.shared.infrastructure.observability.HealthStatuses;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.dao.DataAccessResourceFailureException;

class OutboxHealthIndicatorTest {

    private static final OutboxProperties PROPERTIES =
            new OutboxProperties(Duration.ofSeconds(5), 50, 8, Duration.ofMinutes(5), Duration.ofDays(30), "0 0 3 * * *", true);

    private final OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
    private final OutboxHealthIndicator indicator = new OutboxHealthIndicator(repository, PROPERTIES, 100);

    @Test
    void isUpWhenTheBacklogIsSmallAndNothingHasFailed() {
        when(repository.countPending()).thenReturn(3L);
        when(repository.countFailed()).thenReturn(0L);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("pending", 3L)
                .containsEntry("failed", 0L)
                .containsEntry("pendingThreshold", 100L)
                .containsEntry("maxAttempts", 8);
    }

    @Test
    void isDegradedWhenTheBacklogExceedsTheThreshold() {
        when(repository.countPending()).thenReturn(101L);
        when(repository.countFailed()).thenReturn(0L);

        assertThat(indicator.health().getStatus()).isEqualTo(HealthStatuses.DEGRADED);
    }

    /** Un FAILED no se reintenta solo: aunque sea uno, alguien tiene que mirarlo. */
    @Test
    void isDegradedWhenAnyMessageIsFailed() {
        when(repository.countPending()).thenReturn(0L);
        when(repository.countFailed()).thenReturn(1L);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(HealthStatuses.DEGRADED);
        assertThat(health.getDetails()).containsEntry("failed", 1L);
    }

    @Test
    void isDownWhenTheOutboxCannotEvenBeQueried() {
        when(repository.countPending()).thenThrow(new DataAccessResourceFailureException("sin conexion"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "DataAccessResourceFailureException");
    }

    /** RS-014: el detalle no puede filtrar el mensaje de la excepcion de base de datos. */
    @Test
    void doesNotLeakTheUnderlyingErrorMessage() {
        when(repository.countPending())
                .thenThrow(new DataAccessResourceFailureException("jdbc:postgresql://host/db?password=hunter2"));

        assertThat(indicator.health().getDetails().values()).noneMatch(value -> value.toString().contains("hunter2"));
    }
}
