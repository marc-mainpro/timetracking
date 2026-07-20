package com.tfp.timetracking.outbox.infrastructure;

import com.tfp.timetracking.outbox.application.OutboxWriter;
import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementacion del unico puerto que el resto de modulos usa para escribir
 * en el outbox (ADR-0005). Debe invocarse dentro de la misma transaccion de
 * negocio que origina el evento (T702 lo conectara); aqui solo se persiste el
 * mensaje en estado {@code PENDING}, sin publicarlo.
 */
@Component
public class JpaOutboxWriter implements OutboxWriter {

    private final OutboxMessageRepository outboxMessageRepository;
    private final TenantContext tenantContext;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public JpaOutboxWriter(
            OutboxMessageRepository outboxMessageRepository,
            TenantContext tenantContext,
            Clock clock,
            IdGenerator idGenerator) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public void write(
            String aggregateType, UUID aggregateId, String eventType, int eventVersion, Map<String, Object> payload) {
        Instant now = clock.now();
        OutboxMessage message = new OutboxMessage(
                idGenerator.newId(),
                tenantContext.currentTenantId(),
                aggregateType,
                aggregateId,
                eventType,
                eventVersion,
                Map.copyOf(payload),
                now,
                null,
                0,
                null,
                null,
                OutboxMessageStatus.PENDING,
                now);
        outboxMessageRepository.save(message);
    }
}
