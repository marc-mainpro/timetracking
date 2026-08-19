package com.tfp.timetracking.outbox.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OutboxMessageJpaRepository extends JpaRepository<OutboxMessageJpaEntity, UUID> {

    /**
     * Reclama atomicamente hasta {@code limit} mensajes candidatos
     * (PENDING listos, o PROCESSING huerfanos cuyo lease vencio),
     * bloqueando las filas con {@code FOR UPDATE SKIP LOCKED} para que dos
     * workers concurrentes nunca reclamen la misma fila, y las marca
     * PROCESSING con un nuevo lease en una unica sentencia.
     */
    @Query(
            value =
                    """
                    WITH candidate AS (
                        SELECT id
                        FROM outbox_message
                        WHERE (status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= :now))
                           OR (status = 'PROCESSING' AND next_attempt_at <= :now)
                        ORDER BY created_at
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED
                    )
                    UPDATE outbox_message om
                    SET status = 'PROCESSING', next_attempt_at = :leaseExpiresAt
                    FROM candidate c
                    WHERE om.id = c.id
                    RETURNING om.*
                    """,
            nativeQuery = true)
    List<OutboxMessageJpaEntity> claimBatch(
            @Param("now") Instant now, @Param("limit") int limit, @Param("leaseExpiresAt") Instant leaseExpiresAt);

    @Modifying
    @Query(
            value = "UPDATE outbox_message SET status = 'PUBLISHED', published_at = :publishedAt WHERE id = :id",
            nativeQuery = true)
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);

    @Modifying
    @Query(
            value =
                    """
                    UPDATE outbox_message
                    SET status = 'PENDING', attempts = :attempts, next_attempt_at = :nextAttemptAt, last_error = :lastError
                    WHERE id = :id
                    """,
            nativeQuery = true)
    int markRetry(
            @Param("id") UUID id,
            @Param("attempts") int attempts,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lastError") String lastError);

    @Modifying
    @Query(
            value =
                    "UPDATE outbox_message SET status = 'FAILED', attempts = :attempts, last_error = :lastError WHERE id = :id",
            nativeQuery = true)
    int markFailed(@Param("id") UUID id, @Param("attempts") int attempts, @Param("lastError") String lastError);

    @Modifying
    @Query(value = "DELETE FROM outbox_message WHERE status = 'PUBLISHED' AND published_at < :before", nativeQuery = true)
    int archivePublishedBefore(@Param("before") Instant before);

    /**
     * Reintento manual: solo actua si el mensaje sigue {@code FAILED}. La
     * guarda no es redundante con la comprobacion del caso de uso, es lo que
     * evita que dos administradores concurrentes devuelvan a la cola un mensaje
     * que el publicador ya reclamo, publicandolo dos veces.
     */
    @Modifying
    @Query(
            value =
                    """
                    UPDATE outbox_message
                    SET status = 'PENDING', attempts = 0, next_attempt_at = NULL, last_error = NULL
                    WHERE id = :id AND status = 'FAILED'
                    """,
            nativeQuery = true)
    int requeueFailed(@Param("id") UUID id);

    /** Descarte manual. Misma guarda por estado, y conserva {@code last_error}. */
    @Modifying
    @Query(
            value = "UPDATE outbox_message SET status = 'DISCARDED' WHERE id = :id AND status = 'FAILED'",
            nativeQuery = true)
    int discardFailed(@Param("id") UUID id);

    Page<OutboxMessageJpaEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    @Query("SELECT COUNT(m) FROM OutboxMessageJpaEntity m WHERE m.status IN ('PENDING', 'PROCESSING')")
    long countPending();

    @Query("SELECT COUNT(m) FROM OutboxMessageJpaEntity m WHERE m.status = 'FAILED'")
    long countFailed();
}
