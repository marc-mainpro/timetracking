package com.tfp.timetracking.shift.infrastructure.persistence;

import com.tfp.timetracking.shift.domain.model.ShiftAssignment;
import com.tfp.timetracking.shift.domain.model.ShiftAssignmentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ShiftAssignmentRepositoryAdapter implements ShiftAssignmentRepository {

    private final ShiftAssignmentJpaRepository jpaRepository;

    public ShiftAssignmentRepositoryAdapter(ShiftAssignmentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ShiftAssignment save(ShiftAssignment assignment) {
        return ShiftAssignmentMapper.toDomain(jpaRepository.save(ShiftAssignmentMapper.toJpaEntity(assignment)));
    }

    @Override
    public Optional<ShiftAssignment> findById(UUID tenantId, UUID assignmentId) {
        return jpaRepository.findByTenantIdAndId(tenantId, assignmentId).map(ShiftAssignmentMapper::toDomain);
    }

    @Override
    public List<ShiftAssignment> findByEmployee(UUID tenantId, UUID employeeId) {
        return jpaRepository.findByTenantIdAndEmployeeIdOrderByValidFromAsc(tenantId, employeeId).stream()
                .map(ShiftAssignmentMapper::toDomain)
                .toList();
    }

    @Override
    public List<ShiftAssignment> findEffectiveByEmployee(UUID tenantId, UUID employeeId, LocalDate date) {
        return jpaRepository.findEffectiveByEmployee(tenantId, employeeId, date).stream()
                .map(ShiftAssignmentMapper::toDomain)
                .toList();
    }
}
