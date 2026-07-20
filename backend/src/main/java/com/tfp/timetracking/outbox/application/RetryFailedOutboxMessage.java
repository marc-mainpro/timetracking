package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageNotFailedException;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso operativo (T703): reintento manual de un mensaje de outbox
 * que ya agoto sus intentos automaticos y quedo {@code FAILED}. Pensado
 * para uso humano (operacion/soporte), no lo invoca el publicador
 * automatico.
 *
 * <p>Resetea el mensaje a {@code PENDING} con {@code attempts = 0} y sin
 * {@code nextAttemptAt} (elegible inmediatamente por el proximo {@code
 * claimBatch}) ni {@code lastError} previo.
 */
@Service
public class RetryFailedOutboxMessage {

    private final OutboxMessageRepository repository;

    public RetryFailedOutboxMessage(OutboxMessageRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void retry(UUID messageId) {
        OutboxMessage message = repository
                .findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Mensaje de outbox no encontrado: " + messageId));
        if (message.status() != OutboxMessageStatus.FAILED) {
            throw new OutboxMessageNotFailedException(message.status());
        }
        repository.markRetry(messageId, 0, null, null);
    }
}
