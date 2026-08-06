package com.tfp.timetracking.absence.infrastructure.persistence;

import com.tfp.timetracking.absence.domain.AbsenceRequest;
import com.tfp.timetracking.absence.domain.AbsenceRequestStatus;

final class AbsenceRequestMapper {

    private AbsenceRequestMapper() {}

    static AbsenceRequestJpaEntity toJpaEntity(AbsenceRequest request) {
        return new AbsenceRequestJpaEntity(
                request.id(),
                request.tenantId(),
                request.employeeId(),
                request.absenceTypeId(),
                request.startDate(),
                request.endDate(),
                request.reason(),
                request.status().name(),
                request.resolvedBy(),
                request.resolvedAt(),
                request.resolutionComment(),
                request.createdAt());
    }

    static AbsenceRequest toDomain(AbsenceRequestJpaEntity entity) {
        return AbsenceRequest.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmployeeId(),
                entity.getAbsenceTypeId(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getReason(),
                AbsenceRequestStatus.valueOf(entity.getStatus()),
                entity.getResolvedBy(),
                entity.getResolvedAt(),
                entity.getResolutionComment(),
                entity.getCreatedAt());
    }
}
