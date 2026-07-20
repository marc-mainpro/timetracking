package com.tfp.timetracking.outbox.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    /** Usado por operaciones manuales (T703, {@code RetryFailedOutboxMessage}). */
    Optional<OutboxMessage> findById(UUID id);

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

    /**
     * Marca el mensaje {@code FAILED} definitivamente (T703: intentos
     * agotados). Recibe {@code attempts} para dejar constancia del numero
     * final de intentos realizados (no solo el ultimo error), util para
     * observabilidad/soporte.
     */
    void markFailed(UUID id, int attempts, String lastError);

    /** Purga los mensajes ya publicados antes de {@code before}. Devuelve cuantos se eliminaron. */
    int archivePublishedBefore(Instant before);

    /**
     * Cuenta los mensajes todavia no publicados ni fallidos definitivamente
     * ({@code PENDING} + {@code PROCESSING}), usado como gauge de backlog
     * (T703, metricas Micrometer).
     */
    long countPending();
}
