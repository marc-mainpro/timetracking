package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Mantenimiento manual de la cola de eventos de integracion. */
@Component
public class OutboxFailedQueueMaintenance implements FailedQueueMaintenance {

    private final OutboxMessageRepository repository;
    private final RetryFailedOutboxMessage retryFailedOutboxMessage;
    private final DiscardFailedOutboxMessage discardFailedOutboxMessage;

    public OutboxFailedQueueMaintenance(
            OutboxMessageRepository repository,
            RetryFailedOutboxMessage retryFailedOutboxMessage,
            DiscardFailedOutboxMessage discardFailedOutboxMessage) {
        this.repository = repository;
        this.retryFailedOutboxMessage = retryFailedOutboxMessage;
        this.discardFailedOutboxMessage = discardFailedOutboxMessage;
    }

    @Override
    public String queueName() {
        return "outbox";
    }

    @Override
    public PagedResult<FailedQueueEntry> listFailed(int page, int size) {
        PagedResult<OutboxMessage> failed = repository.findByStatus(OutboxMessageStatus.FAILED, page, size);
        return new PagedResult<>(
                failed.content().stream().map(OutboxFailedQueueMaintenance::toEntry).toList(),
                failed.page(),
                failed.size(),
                failed.totalElements(),
                failed.totalPages());
    }

    @Override
    public FailedQueueEntry retry(UUID id) {
        return toEntry(retryFailedOutboxMessage.retry(id));
    }

    @Override
    public FailedQueueEntry discard(UUID id) {
        return toEntry(discardFailedOutboxMessage.discard(id));
    }

    /**
     * El payload del evento no se expone: identifica el mensaje por su tipo y
     * su agregado, que es cuanto hace falta para rastrearlo, sin volcar datos
     * de negocio de todos los tenants en una pantalla de plataforma.
     */
    private static FailedQueueEntry toEntry(OutboxMessage message) {
        return new FailedQueueEntry(
                message.id(),
                message.tenantId(),
                message.eventType(),
                message.aggregateType() + "#" + message.aggregateId(),
                message.attempts(),
                message.lastError(),
                message.occurredAt());
    }
}
