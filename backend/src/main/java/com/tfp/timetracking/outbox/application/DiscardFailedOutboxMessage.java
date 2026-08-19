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
 * Descarte manual de un mensaje de outbox que agoto sus intentos: se renuncia
 * a publicarlo. Simetrico a {@link RetryFailedOutboxMessage}, y como el, es una
 * operacion humana de operacion/soporte.
 *
 * <p>La fila <b>no se borra</b>: queda en {@code DISCARDED} conservando su
 * {@code lastError}, de modo que deja de contar como incidencia pendiente sin
 * perder la evidencia de que fallo. El motivo y el actor quedan en auditoria.
 */
@Service
public class DiscardFailedOutboxMessage {

    private final OutboxMessageRepository repository;

    public DiscardFailedOutboxMessage(OutboxMessageRepository repository) {
        this.repository = repository;
    }

    /** @return el mensaje tal como estaba antes de descartarlo */
    @Transactional
    public OutboxMessage discard(UUID messageId) {
        OutboxMessage message = repository
                .findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Mensaje de outbox no encontrado: " + messageId));
        if (message.status() != OutboxMessageStatus.FAILED) {
            throw new OutboxMessageNotFailedException(message.status());
        }
        // La comprobacion anterior distingue 404 de 409; esta guarda cierra la
        // carrera con otro administrador entre la lectura y la escritura.
        if (!repository.discardFailed(messageId)) {
            throw new OutboxMessageNotFailedException(currentStatus(messageId));
        }
        return message;
    }

    /** Estado real tras perder la carrera, para no informar de uno ya caduco. */
    private OutboxMessageStatus currentStatus(UUID messageId) {
        return repository
                .findById(messageId)
                .map(OutboxMessage::status)
                .orElseThrow(() -> new ResourceNotFoundException("Mensaje de outbox no encontrado: " + messageId));
    }
}
