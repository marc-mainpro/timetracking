package com.tfp.timetracking.outbox.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Mensaje del Transactional Outbox (ADR-0005, SDD §14). Representa un evento
 * de integracion pendiente/en curso/publicado/fallido. Nunca contiene
 * entidades JPA ni modelos internos: {@code payload} es un mapa serializable
 * ya preparado por el productor del evento.
 */
public record OutboxMessage(
        UUID id,
        UUID tenantId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        int eventVersion,
        Map<String, Object> payload,
        Instant occurredAt,
        Instant publishedAt,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        OutboxMessageStatus status,
        Instant createdAt) {}
