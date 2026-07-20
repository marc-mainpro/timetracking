package com.tfp.timetracking.outbox.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import java.util.Map;

final class OutboxMessageMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private OutboxMessageMapper() {}

    static OutboxMessageJpaEntity toJpaEntity(OutboxMessage message, ObjectMapper objectMapper) {
        return new OutboxMessageJpaEntity(
                message.id(),
                message.tenantId(),
                message.aggregateType(),
                message.aggregateId(),
                message.eventType(),
                message.eventVersion(),
                writeJson(message.payload(), objectMapper),
                message.occurredAt(),
                message.publishedAt(),
                message.attempts(),
                message.nextAttemptAt(),
                message.lastError(),
                message.status().name(),
                message.createdAt());
    }

    static OutboxMessage toDomain(OutboxMessageJpaEntity entity, ObjectMapper objectMapper) {
        return new OutboxMessage(
                entity.getId(),
                entity.getTenantId(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getEventType(),
                entity.getEventVersion(),
                readJson(entity.getPayload(), objectMapper),
                entity.getOccurredAt(),
                entity.getPublishedAt(),
                entity.getAttempts(),
                entity.getNextAttemptAt(),
                entity.getLastError(),
                OutboxMessageStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt());
    }

    private static String writeJson(Map<String, Object> payload, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el payload del mensaje de outbox", ex);
        }
    }

    private static Map<String, Object> readJson(String payload, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo deserializar el payload del mensaje de outbox", ex);
        }
    }
}
