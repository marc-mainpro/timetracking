package com.tfp.timetracking.audit.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.audit.domain.AuditEvent;
import java.util.Map;

final class AuditEventMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private AuditEventMapper() {}

    static AuditEventJpaEntity toJpaEntity(AuditEvent auditEvent, ObjectMapper objectMapper) {
        return new AuditEventJpaEntity(
                auditEvent.id(),
                auditEvent.tenantId(),
                auditEvent.actorUserId(),
                auditEvent.action(),
                auditEvent.entityType(),
                auditEvent.entityId(),
                auditEvent.correlationId(),
                writeJson(auditEvent.metadata(), objectMapper),
                auditEvent.occurredAt());
    }

    static AuditEvent toDomain(AuditEventJpaEntity entity, ObjectMapper objectMapper) {
        return new AuditEvent(
                entity.getId(),
                entity.getTenantId(),
                entity.getActorUserId(),
                entity.getAction(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getCorrelationId(),
                readJson(entity.getMetadata(), objectMapper),
                entity.getOccurredAt());
    }

    private static String writeJson(Map<String, Object> metadata, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar metadata de auditoria", ex);
        }
    }

    private static Map<String, Object> readJson(String metadata, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(metadata, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo deserializar metadata de auditoria", ex);
        }
    }
}
