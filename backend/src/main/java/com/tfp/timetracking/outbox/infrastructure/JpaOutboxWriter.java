package com.tfp.timetracking.outbox.infrastructure;

import com.tfp.timetracking.outbox.application.OutboxWriter;
import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import org.springframework.stereotype.Component;

/**
 * Implementacion del unico puerto que el resto de modulos usa para escribir
 * en el outbox (ADR-0005). Debe invocarse dentro de la misma transaccion de
 * negocio que origina el evento (T702 la conecta via
 * {@code OutboxDomainEventPublisher}); aqui solo se persiste el mensaje en
 * estado {@code PENDING}, sin publicarlo.
 *
 * <p>El {@code tenantId} de la fila persistida se toma de {@link
 * IntegrationEvent#tenantId()} (y no de {@code TenantContext}, a diferencia
 * de otros repositorios tenant-aware de la aplicacion): el evento de dominio
 * de origen ya lo lleva, calculado por el propio agregado con datos internos
 * y validados (nunca a partir de un valor de request sin validar), y algunas
 * acciones que publican eventos son deliberadamente anonimas (p.ej. {@code
 * RegisterTenantUseCase}, el alta de un tenant nuevo, se ejecuta en el
 * endpoint publico de registro, sin JWT ni {@code TenantContext} activo
 * todavia). Exigir aqui un {@code TenantContext} autenticado romperia ese
 * flujo.
 */
@Component
public class JpaOutboxWriter implements OutboxWriter {

    private final OutboxMessageRepository outboxMessageRepository;
    private final Clock clock;

    public JpaOutboxWriter(OutboxMessageRepository outboxMessageRepository, Clock clock) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.clock = clock;
    }

    @Override
    public void write(IntegrationEvent event) {
        OutboxMessage message = new OutboxMessage(
                event.eventId(),
                event.tenantId(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.eventVersion(),
                event.payload(),
                event.occurredAt(),
                null,
                0,
                null,
                null,
                OutboxMessageStatus.PENDING,
                clock.now());
        outboxMessageRepository.save(message);
    }
}
