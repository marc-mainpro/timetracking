package com.tfp.timetracking.shift.application;

import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shift.domain.model.ShiftAssignment;
import com.tfp.timetracking.shift.domain.model.ShiftAssignmentRepository;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateArchivedException;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignShiftUseCase {

    private final ShiftAssignmentRepository assignmentRepository;
    private final ShiftTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;

    public AssignShiftUseCase(
            ShiftAssignmentRepository assignmentRepository,
            ShiftTemplateRepository templateRepository,
            UserRepository userRepository,
            TenantContext tenantContext) {
        this.assignmentRepository = assignmentRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public ShiftAssignment assign(AssignShiftCommand command) {
        ShiftTemplate template = templateRepository.findById(tenantContext.currentTenantId(), command.shiftTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de turno no encontrada"));
        if (template.status() == com.tfp.timetracking.shift.domain.model.ShiftTemplateStatus.ARCHIVED) {
            throw new ShiftTemplateArchivedException();
        }
        userRepository.findById(tenantContext.currentTenantId(), command.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado"));
        ShiftAssignment assignment = ShiftAssignment.create(
                tenantContext.currentTenantId(),
                command.employeeId(),
                command.shiftTemplateId(),
                command.validFrom(),
                command.validTo(),
                java.util.UUID.randomUUID());
        return assignmentRepository.save(assignment);
    }
}
