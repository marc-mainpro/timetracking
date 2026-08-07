package com.tfp.timetracking.shift.infrastructure.persistence;

import com.tfp.timetracking.shift.domain.model.ShiftAssignment;

final class ShiftAssignmentMapper {

    private ShiftAssignmentMapper() {}

    static ShiftAssignmentJpaEntity toJpaEntity(ShiftAssignment assignment) {
        return new ShiftAssignmentJpaEntity(
                assignment.id(),
                assignment.tenantId(),
                assignment.employeeId(),
                assignment.shiftTemplateId(),
                assignment.validFrom(),
                assignment.validTo());
    }

    static ShiftAssignment toDomain(ShiftAssignmentJpaEntity entity) {
        return ShiftAssignment.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmployeeId(),
                entity.getShiftTemplateId(),
                entity.getValidFrom(),
                entity.getValidTo());
    }
}
