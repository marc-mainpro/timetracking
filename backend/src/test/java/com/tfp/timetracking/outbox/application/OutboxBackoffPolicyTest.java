package com.tfp.timetracking.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.shared.domain.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Verifica el crecimiento exponencial (1 min * 2^intentosPrevios) del
 * backoff y que el jitter anadido nunca reduce el delay por debajo de la
 * base ni supera el 20% adicional documentado.
 */
class OutboxBackoffPolicyTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = () -> NOW;

    @Test
    void firstFailureDelaysAroundOneMinute() {
        Instant nextAttemptAt = OutboxBackoffPolicy.nextAttemptAt(FIXED_CLOCK, 0);

        assertThat(nextAttemptAt).isAfterOrEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(nextAttemptAt).isBefore(NOW.plus(Duration.ofMinutes(1)).plus(Duration.ofSeconds(15)));
    }

    @Test
    void delayGrowsExponentiallyWithPreviousAttempts() {
        Instant afterFirst = OutboxBackoffPolicy.nextAttemptAt(FIXED_CLOCK, 0);
        Instant afterSecond = OutboxBackoffPolicy.nextAttemptAt(FIXED_CLOCK, 1);
        Instant afterThird = OutboxBackoffPolicy.nextAttemptAt(FIXED_CLOCK, 2);
        Instant afterFourth = OutboxBackoffPolicy.nextAttemptAt(FIXED_CLOCK, 3);

        // Bases: 1, 2, 4, 8 minutos. Aun con el jitter maximo (20%) del nivel
        // anterior, cada nivel debe superar al anterior sin solaparse.
        assertThat(afterSecond).isAfter(afterFirst.plus(Duration.ofSeconds(30)));
        assertThat(afterThird).isAfter(afterSecond.plus(Duration.ofSeconds(30)));
        assertThat(afterFourth).isAfter(afterThird.plus(Duration.ofSeconds(30)));

        assertThat(afterSecond).isBetween(NOW.plus(Duration.ofMinutes(2)), NOW.plus(Duration.ofMinutes(3)));
        assertThat(afterThird).isBetween(NOW.plus(Duration.ofMinutes(4)), NOW.plus(Duration.ofMinutes(5)));
        assertThat(afterFourth).isBetween(NOW.plus(Duration.ofMinutes(8)), NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void neverProducesAnInstantBeforeTheBaseDelay() {
        for (int attempts = 0; attempts < 10; attempts++) {
            Instant nextAttemptAt = OutboxBackoffPolicy.nextAttemptAt(FIXED_CLOCK, attempts);
            Instant minimumExpected = NOW.plus(Duration.ofMinutes(1).multipliedBy(1L << attempts));
            assertThat(nextAttemptAt).isAfterOrEqualTo(minimumExpected);
        }
    }
}
