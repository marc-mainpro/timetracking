package com.tfp.timetracking.corrections.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestStatus;
import com.tfp.timetracking.corrections.domain.ProposedChanges;

final class CorrectionRequestMapper {

    private CorrectionRequestMapper() {}

    static CorrectionRequestJpaEntity toJpaEntity(CorrectionRequest correctionRequest, ObjectMapper objectMapper) {
        return new CorrectionRequestJpaEntity(
                correctionRequest.id(),
                correctionRequest.tenantId(),
                correctionRequest.workdayId(),
                correctionRequest.requestedBy(),
                correctionRequest.reason(),
                writeJson(correctionRequest.proposedChanges(), objectMapper),
                correctionRequest.status().name(),
                correctionRequest.resolvedBy(),
                correctionRequest.resolvedAt(),
                correctionRequest.resolutionComment(),
                correctionRequest.createdAt(),
                correctionRequest.version());
    }

    static CorrectionRequest toDomain(CorrectionRequestJpaEntity entity, ObjectMapper objectMapper) {
        return CorrectionRequest.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getWorkdayId(),
                entity.getRequestedBy(),
                entity.getReason(),
                readJson(entity.getProposedChanges(), objectMapper),
                CorrectionRequestStatus.valueOf(entity.getStatus()),
                entity.getResolvedBy(),
                entity.getResolvedAt(),
                entity.getResolutionComment(),
                entity.getVersion(),
                entity.getCreatedAt());
    }

    private static String writeJson(ProposedChanges proposedChanges, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(proposedChanges);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar proposedChanges", ex);
        }
    }

    private static ProposedChanges readJson(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, ProposedChanges.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo deserializar proposedChanges", ex);
        }
    }
}
