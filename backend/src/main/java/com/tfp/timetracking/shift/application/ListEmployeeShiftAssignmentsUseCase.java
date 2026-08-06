package com.tfp.timetracking.shift.application;

import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shift.domain.model.ShiftAssignment;
import com.tfp.timetracking.shift.domain.model.ShiftAssignmentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListEmployeeShiftAssignmentsUseCase {

    private final ShiftAssignmentRepository repository;
    private final TenantContext tenantContext;

    public ListEmployeeShiftAssignmentsUseCase(ShiftAssignmentRepository repository, TenantContext tenantContext) {
        this.repository = repository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public List<ShiftAssignment> listOwnEffective(LocalDate date) {
        return repository.findEffectiveByEmployee(tenantContext.currentTenantId(), tenantContext.currentUserId(), date);
    }

    @Transactional(readOnly = true)
    public List<ShiftAssignment> listEmployee(UUID employeeId) {
        return repository.findByEmployee(tenantContext.currentTenantId(), employeeId);
    }
}
