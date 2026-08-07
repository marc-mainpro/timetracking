package com.tfp.timetracking.shift.application;

import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shift.domain.model.ShiftBreakPolicy;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateRepository;
import java.time.Duration;
import java.util.UUID;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateShiftTemplateUseCase {

    private final ShiftTemplateRepository repository;
    private final TenantContext tenantContext;
    private final AuditRecorder auditRecorder;

    public UpdateShiftTemplateUseCase(ShiftTemplateRepository repository, TenantContext tenantContext, AuditRecorder auditRecorder) {
        this.repository = repository;
        this.tenantContext = tenantContext;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public ShiftTemplate update(UUID shiftTemplateId, SaveShiftTemplateCommand command) {
        ShiftTemplate template = repository.findById(tenantContext.currentTenantId(), shiftTemplateId)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de turno no encontrada"));
        template.update(
                command.name(),
                command.startTime(),
                command.endTime(),
                new ShiftBreakPolicy(command.plannedBreakMinutes() != null ? Duration.ofMinutes(command.plannedBreakMinutes()) : Duration.ZERO));
        var audited = repository.save(template);
        auditRecorder.record("SHIFT_TEMPLATE_UPDATED", "ShiftTemplate", audited.id(), Map.of("name", audited.name(), "startTime", audited.startTime().toString(), "endTime", audited.endTime().toString()));
        return audited;
    }
}
