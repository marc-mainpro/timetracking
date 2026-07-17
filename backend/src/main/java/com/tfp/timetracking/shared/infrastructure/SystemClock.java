package com.tfp.timetracking.shared.infrastructure;

import com.tfp.timetracking.shared.domain.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Implementacion de infraestructura del puerto {@link Clock} basada en el
 * reloj del sistema (UTC). Se persiste siempre en {@link Instant} (UTC),
 * segun CONTEXT-GLOBAL §3.
 */
@Component
public class SystemClock implements Clock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
