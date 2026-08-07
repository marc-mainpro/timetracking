package com.tfp.timetracking.outbox.infrastructure;

import com.tfp.timetracking.outbox.application.QueueStatusContributor;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import org.springframework.stereotype.Component;

/** Estado de la cola de eventos de integracion (T140-05). */
@Component
public class OutboxQueueStatusContributor implements QueueStatusContributor {

    private final OutboxMessageRepository outboxMessageRepository;

    public OutboxQueueStatusContributor(OutboxMessageRepository outboxMessageRepository) {
        this.outboxMessageRepository = outboxMessageRepository;
    }

    @Override
    public QueueStatus status() {
        return new QueueStatus("outbox", outboxMessageRepository.countPending(), outboxMessageRepository.countFailed());
    }
}
