package com.tfp.timetracking.outbox.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de dominio para la persistencia de {@link OutboxMessage}. Solo lo
 * implementa infraestructura ({@code outbox.infrastructure.persistence}) y
 * solo lo consume el propio modulo {@code outbox} (el publicador de T703).
 * El resto de modulos nunca dependen de este puerto: usan
 * {@code outbox.application.OutboxWriter}.
 */
public interface OutboxMessageRepository {

    OutboxMessage save(OutboxMessage message);

    /**
     * Reclama hasta {@code limit} mensajes listos para publicarse: los
     * {@code PENDING} cuyo {@code nextAttemptAt} ya vencio (o no tiene) y los
     * {@code PROCESSING} "huerfanos" cuyo {@code nextAttemptAt} (usado como
     * lease de visibilidad) tambien vencio. Los marca {@code PROCESSING} y les
     * fija {@code nextAttemptAt = leaseExpiresAt} de forma atomica mediante
     * {@code FOR UPDATE SKIP LOCKED}, de modo que dos workers concurrentes
     * nunca reclaman el mismo mensaje.
     */
    List<OutboxMessage> claimBatch(int limit, Instant now, Instant leaseExpiresAt);

    void markPublished(UUID id, Instant publishedAt);

    void markRetry(UUID id, int attempts, Instant nextAttemptAt, String lastError);

    void markFailed(UUID id, String lastError);

    /** Purga los mensajes ya publicados antes de {@code before}. Devuelve cuantos se eliminaron. */
    int archivePublishedBefore(Instant before);
}
