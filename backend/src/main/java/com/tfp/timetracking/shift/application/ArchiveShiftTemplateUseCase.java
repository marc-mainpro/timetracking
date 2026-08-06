package com.tfp.timetracking.shift.application;

import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchiveShiftTemplateUseCase {

    private final ShiftTemplateRepository repository;
    private final TenantContext tenantContext;

    public ArchiveShiftTemplateUseCase(ShiftTemplateRepository repository, TenantContext tenantContext) {
        this.repository = repository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public ShiftTemplate archive(UUID shiftTemplateId) {
        ShiftTemplate template = repository.findById(tenantContext.currentTenantId(), shiftTemplateId)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de turno no encontrada"));
        template.archive();
        return repository.save(template);
    }
}
