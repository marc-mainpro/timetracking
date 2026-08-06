package com.tfp.timetracking.shift.application;

import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListShiftTemplatesUseCase {

    private final ShiftTemplateRepository repository;
    private final TenantContext tenantContext;

    public ListShiftTemplatesUseCase(ShiftTemplateRepository repository, TenantContext tenantContext) {
        this.repository = repository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public List<ShiftTemplate> list() {
        return repository.findByTenantId(tenantContext.currentTenantId());
    }
}
