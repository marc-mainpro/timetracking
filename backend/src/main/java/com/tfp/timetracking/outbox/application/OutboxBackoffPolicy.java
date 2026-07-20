package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.shared.domain.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Calcula el siguiente instante de reintento tras un fallo de publicacion
 * (T703): backoff exponencial de base 1 minuto (<code>1 min * 2^intentosPrevios</code>)
 * mas jitter aleatorio (hasta un 20% adicional), para evitar que reintentos
 * de muchos mensajes fallidos a la vez converjan en el mismo instante
 * ("thundering herd").
 *
 * <p>Paquete-privada: solo la usa {@link PublishPendingOutboxMessages}.
 */
final class OutboxBackoffPolicy {

    private static final Duration BASE_DELAY = Duration.ofMinutes(1);
    private static final double JITTER_RATIO = 0.2;
    private static final int MAX_SHIFT = 30; // evita overflow para maxAttempts extremos

    private OutboxBackoffPolicy() {}

    /**
     * @param attemptsBeforeThisFailure numero de intentos ya realizados
     *     antes del que acaba de fallar (0 en el primer fallo)
     */
    static Instant nextAttemptAt(Clock clock, int attemptsBeforeThisFailure) {
        int shift = Math.min(Math.max(attemptsBeforeThisFailure, 0), MAX_SHIFT);
        long baseMillis = BASE_DELAY.toMillis() * (1L << shift);
        long maxJitterMillis = (long) (baseMillis * JITTER_RATIO);
        long jitterMillis = maxJitterMillis > 0 ? ThreadLocalRandom.current().nextLong(0, maxJitterMillis) : 0;
        return clock.now().plusMillis(baseMillis + jitterMillis);
    }
}
