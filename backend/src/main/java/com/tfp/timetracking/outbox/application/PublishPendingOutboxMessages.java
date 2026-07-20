package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Caso de uso central del publicador de outbox (T703, ADR-0005): reclama un
 * lote de mensajes listos (via {@link OutboxMessageRepository#claimBatch})
 * y, por cada uno, intenta entregarlo a traves de {@link
 * IntegrationEventPublisher}.
 *
 * <ul>
 *   <li>Exito: marca el mensaje {@code PUBLISHED}.
 *   <li>Fallo con intentos restantes: programa un reintento con backoff
 *       exponencial + jitter ({@link OutboxBackoffPolicy}).
 *   <li>Fallo tras agotar {@code outbox.max-attempts}: marca el mensaje
 *       {@code FAILED} con el ultimo error.
 * </ul>
 *
 * <p>Invocable tanto desde el scheduler ({@code
 * outbox.infrastructure.OutboxPublisherJob}) como directamente desde tests u
 * otras herramientas operativas (no depende de que el cron dispare para
 * ejercitarse). No lleva {@code @Transactional} a nivel de metodo a
 * proposito: cada llamada al repositorio ({@code claimBatch},
 * {@code markPublished}, {@code markRetry}, {@code markFailed}) ya abre su
 * propia transaccion corta (ver {@code OutboxMessageRepositoryAdapter}), de
 * modo que el fallo (o la lentitud) al publicar UN mensaje no mantiene
 * bloqueadas las filas de los demas mensajes del lote.
 */
@Service
public class PublishPendingOutboxMessages {

    private static final Logger log = LoggerFactory.getLogger(PublishPendingOutboxMessages.class);

    private final OutboxMessageRepository repository;
    private final IntegrationEventPublisher publisher;
    private final Clock clock;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;

    public PublishPendingOutboxMessages(
            OutboxMessageRepository repository,
            IntegrationEventPublisher publisher,
            Clock clock,
            OutboxProperties properties,
            OutboxMetrics metrics) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * Reclama y procesa hasta {@code outbox.batch-size} mensajes.
     *
     * @return el numero de mensajes reclamados (procesados, con exito o sin
     *     el) en esta invocacion
     */
    public int publishBatch() {
        Instant now = clock.now();
        Instant leaseExpiresAt = now.plus(properties.claimTimeout());
        List<OutboxMessage> claimed = repository.claimBatch(properties.batchSize(), now, leaseExpiresAt);
        for (OutboxMessage message : claimed) {
            publishOne(message);
        }
        return claimed.size();
    }

    private void publishOne(OutboxMessage message) {
        Timer.Sample sample = metrics.startTimer();
        try {
            publisher.publish(toIntegrationEvent(message));
            repository.markPublished(message.id(), clock.now());
            metrics.recordPublished();
        } catch (Exception ex) {
            handleFailure(message, ex);
        } finally {
            metrics.stopTimer(sample);
        }
    }

    private void handleFailure(OutboxMessage message, Exception ex) {
        int attemptsBefore = message.attempts();
        int attemptsAfter = attemptsBefore + 1;
        String lastError = truncate(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName());
        if (attemptsAfter >= properties.maxAttempts()) {
            repository.markFailed(message.id(), attemptsAfter, lastError);
            metrics.recordFailed();
            log.warn(
                    "outbox.publish.failed id={} eventType={} attempts={} maxAttempts={} error={}",
                    message.id(),
                    message.eventType(),
                    attemptsAfter,
                    properties.maxAttempts(),
                    lastError,
                    ex);
        } else {
            Instant nextAttemptAt = OutboxBackoffPolicy.nextAttemptAt(clock, attemptsBefore);
            repository.markRetry(message.id(), attemptsAfter, nextAttemptAt, lastError);
            metrics.recordRetried();
            log.warn(
                    "outbox.publish.retry id={} eventType={} attempts={} nextAttemptAt={} error={}",
                    message.id(),
                    message.eventType(),
                    attemptsAfter,
                    nextAttemptAt,
                    lastError,
                    ex);
        }
    }

    private static IntegrationEvent toIntegrationEvent(OutboxMessage message) {
        return new IntegrationEvent(
                message.id(),
                message.eventType(),
                message.eventVersion(),
                message.occurredAt(),
                message.tenantId(),
                message.aggregateId(),
                message.aggregateType(),
                message.payload());
    }

    private static String truncate(String message) {
        int maxLength = 2000;
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }
}
