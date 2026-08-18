package com.tfp.timetracking.shift.application;

import com.tfp.timetracking.shared.application.EmployeeAssignmentTargetQuery;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.TargetNotEmployeeException;
import com.tfp.timetracking.shift.domain.model.ShiftAssignment;
import com.tfp.timetracking.shift.domain.model.ShiftAssignmentRepository;
import com.tfp.timetracking.shift.domain.model.OverlappingShiftAssignmentException;
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
    private final EmployeeAssignmentTargetQuery employeeAssignmentTargetQuery;
    private final TenantContext tenantContext;
    private final AuditRecorder auditRecorder;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public AssignShiftUseCase(
            ShiftAssignmentRepository assignmentRepository,
            ShiftTemplateRepository templateRepository,
            EmployeeAssignmentTargetQuery employeeAssignmentTargetQuery,
            TenantContext tenantContext,
            AuditRecorder auditRecorder,
            DomainEventPublisher domainEventPublisher,
            Clock clock,
            IdGenerator idGenerator) {
        this.assignmentRepository = assignmentRepository;
        this.templateRepository = templateRepository;
        this.employeeAssignmentTargetQuery = employeeAssignmentTargetQuery;
        this.tenantContext = tenantContext;
        this.auditRecorder = auditRecorder;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public ShiftAssignment assign(AssignShiftCommand command) {
        ShiftTemplate template = templateRepository.findById(tenantContext.currentTenantId(), command.shiftTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de turno no encontrada"));
        if (template.status() == com.tfp.timetracking.shift.domain.model.ShiftTemplateStatus.ARCHIVED) {
            throw new ShiftTemplateArchivedException();
        }
        // Un turno solo significa algo sobre alguien que ficha: los endpoints de
        // jornada exigen el rol EMPLOYEE, asi que asignarselo a un administrador
        // que no lo tiene crearia una planificacion inservible.
        switch (employeeAssignmentTargetQuery.check(tenantContext.currentTenantId(), command.employeeId())) {
            case UNKNOWN -> throw new ResourceNotFoundException("Empleado no encontrado");
            case NOT_EMPLOYEE -> throw new TargetNotEmployeeException();
            case ASSIGNABLE -> { }
        }
        ShiftAssignment assignment = ShiftAssignment.assign(
                tenantContext.currentTenantId(),
                command.employeeId(),
                command.shiftTemplateId(),
                template.name(),
                command.validFrom(),
                command.validTo(),
                idGenerator.newId(),
                clock.now(),
                idGenerator);
        boolean overlapsExistingAssignment = assignmentRepository.findByEmployee(tenantContext.currentTenantId(), command.employeeId())
                .stream()
                .anyMatch(existing -> existing.overlaps(assignment));
        if (overlapsExistingAssignment) {
            throw new OverlappingShiftAssignmentException();
        }
        var audited = assignmentRepository.save(assignment);
        // Dentro de la misma transaccion que la asignacion: el evento se
        // escribe en el outbox o no se escribe nada (RF-OUT-001).
        domainEventPublisher.publish(assignment.pullDomainEvents());
        auditRecorder.record("SHIFT_ASSIGNED", "ShiftAssignment", audited.id(), Map.of("employeeId", audited.employeeId().toString(), "shiftTemplateId", audited.shiftTemplateId().toString(), "validFrom", audited.validFrom().toString()));
        return audited;
    }
}
