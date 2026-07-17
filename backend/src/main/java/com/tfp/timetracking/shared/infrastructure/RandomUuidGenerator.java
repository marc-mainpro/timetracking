package com.tfp.timetracking.shared.infrastructure;

import com.tfp.timetracking.shared.domain.IdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementacion de infraestructura del puerto {@link IdGenerator} basada en
 * {@link UUID#randomUUID()} (UUID v4).
 */
@Component
public class RandomUuidGenerator implements IdGenerator {

    @Override
    public UUID newId() {
        return UUID.randomUUID();
    }
}
