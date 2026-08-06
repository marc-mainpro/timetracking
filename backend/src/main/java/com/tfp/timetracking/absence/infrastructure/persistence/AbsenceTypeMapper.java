package com.tfp.timetracking.absence.infrastructure.persistence;

import com.tfp.timetracking.absence.domain.AbsenceType;

final class AbsenceTypeMapper {

    private AbsenceTypeMapper() {}

    static AbsenceTypeJpaEntity toJpaEntity(AbsenceType type) {
        return new AbsenceTypeJpaEntity(
                type.id(),
                type.tenantId(),
                type.code(),
                type.name(),
                type.requiresApproval(),
                type.allowsAttachment(),
                type.active());
    }

    static AbsenceType toDomain(AbsenceTypeJpaEntity entity) {
        return AbsenceType.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getCode(),
                entity.getName(),
                entity.isRequiresApproval(),
                entity.isAllowsAttachment(),
                entity.isActive());
    }
}
