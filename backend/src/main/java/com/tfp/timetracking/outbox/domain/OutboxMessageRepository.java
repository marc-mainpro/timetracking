package com.tfp.timetracking.outbox.domain;

import com.tfp.timetracking.shared.domain.PagedResult;
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

    /**
     * Mensajes en un estado, del mas antiguo al mas reciente, paginados.
     *
     * <p>No lleva {@code tenantId}: la consume el panel tecnico de plataforma,
     * que vigila las colas del sistema entero. Es la misma excepcion justificada
     * que ya aplican {@link #countPending()} y {@link #countFailed()}, y no debe
     * reutilizarse desde ningun flujo de usuario.
     */
    PagedResult<OutboxMessage> findByStatus(OutboxMessageStatus status, int page, int size);

    /**
     * Devuelve a {@code PENDING} un mensaje {@code FAILED} (reintento manual),
     * con los intentos a cero y sin error previo ni {@code nextAttemptAt}.
     *
     * <p>Filtra por estado <b>en la propia sentencia</b>: entre la comprobacion
     * del caso de uso y la escritura, otro administrador puede haber reintentado
     * el mensaje y el publicador haberlo reclamado ya. Escribir sin la guarda
     * devolveria a la cola un mensaje en vuelo y lo publicaria dos veces.
     *
     * @return {@code true} si el mensaje seguia {@code FAILED} y se actualizo
     */
    boolean requeueFailed(UUID id);

    /**
     * Marca {@code DISCARDED} un mensaje {@code FAILED}: se renuncia a
     * publicarlo. Conserva la fila y su {@code lastError} como traza; el motivo
     * y el actor quedan en auditoria. Misma guarda por estado que {@link
     * #requeueFailed(UUID)}.
     *
     * @return {@code true} si el mensaje seguia {@code FAILED} y se actualizo
     */
    boolean discardFailed(UUID id);

    /** Purga los mensajes ya publicados antes de {@code before}. Devuelve cuantos se eliminaron. */
    int archivePublishedBefore(Instant before);

    /**
     * Cuenta los mensajes todavia no publicados ni fallidos definitivamente
     * ({@code PENDING} + {@code PROCESSING}), usado como gauge de backlog
     * (T703, metricas Micrometer).
     */
    long countPending();

    /**
     * Cuenta los mensajes {@code FAILED} (intentos agotados). A diferencia del
     * backlog {@code PENDING}, que se drena solo, un {@code FAILED} no se
     * reintenta nunca mas sin intervencion: por eso alimenta el health check
     * operativo del outbox (T140-03) y no solo un gauge informativo.
     */
    long countFailed();
}
