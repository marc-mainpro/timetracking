package com.tfp.timetracking.shift.application;

import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shift.domain.model.ShiftAssignment;
import com.tfp.timetracking.shift.domain.model.ShiftAssignmentRepository;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateArchivedException;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignShiftUseCase {

    private final ShiftAssignmentRepository assignmentRepository;
    private final ShiftTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;
    private final AuditRecorder auditRecorder;

    public AssignShiftUseCase(
            ShiftAssignmentRepository assignmentRepository,
            ShiftTemplateRepository templateRepository,
            UserRepository userRepository,
            TenantContext tenantContext, AuditRecorder auditRecorder) {
        this.assignmentRepository = assignmentRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
        this.auditRecorder = auditRecorder;
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
        var audited = assignmentRepository.save(assignment);
        auditRecorder.record("SHIFT_ASSIGNED", "ShiftAssignment", audited.id(), Map.of("employeeId", audited.employeeId().toString(), "shiftTemplateId", audited.shiftTemplateId().toString(), "validFrom", audited.validFrom().toString()));
        return audited;
    }
}
