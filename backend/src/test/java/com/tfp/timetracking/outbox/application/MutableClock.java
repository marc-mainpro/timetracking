package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.shared.domain.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Doble de prueba de {@link Clock} con instante mutable, usado por los tests
 * de integracion del publicador de outbox (T703) para simular el paso del
 * tiempo (backoff, expiracion de lease) sin sleeps arbitrarios: se avanza
 * explicitamente entre llamadas al caso de uso bajo prueba.
 */
public class MutableClock implements Clock {

    private final AtomicReference<Instant> instant;

    public MutableClock(Instant initial) {
        this.instant = new AtomicReference<>(initial);
    }

    @Override
    public Instant now() {
        return instant.get();
    }

    public void setInstant(Instant newInstant) {
        instant.set(newInstant);
    }

    public void advanceBy(Duration duration) {
        instant.updateAndGet(current -> current.plus(duration));
    }
}
