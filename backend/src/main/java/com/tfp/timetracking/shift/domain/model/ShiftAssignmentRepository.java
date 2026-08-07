package com.tfp.timetracking.shift.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftAssignmentRepository {

    ShiftAssignment save(ShiftAssignment assignment);

    Optional<ShiftAssignment> findById(UUID tenantId, UUID assignmentId);

    List<ShiftAssignment> findByEmployee(UUID tenantId, UUID employeeId);

    List<ShiftAssignment> findEffectiveByEmployee(UUID tenantId, UUID employeeId, LocalDate date);
}
