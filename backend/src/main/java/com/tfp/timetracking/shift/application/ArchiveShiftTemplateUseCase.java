package com.tfp.timetracking.shift.application;

import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateRepository;
import java.util.UUID;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchiveShiftTemplateUseCase {

    private final ShiftTemplateRepository repository;
    private final TenantContext tenantContext;
    private final AuditRecorder auditRecorder;

    public ArchiveShiftTemplateUseCase(ShiftTemplateRepository repository, TenantContext tenantContext, AuditRecorder auditRecorder) {
        this.repository = repository;
        this.tenantContext = tenantContext;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public ShiftTemplate archive(UUID shiftTemplateId) {
        ShiftTemplate template = repository.findById(tenantContext.currentTenantId(), shiftTemplateId)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de turno no encontrada"));
        template.archive();
        var audited = repository.save(template);
        auditRecorder.record("SHIFT_TEMPLATE_ARCHIVED", "ShiftTemplate", audited.id(), Map.of("name", audited.name()));
        return audited;
    }
}
