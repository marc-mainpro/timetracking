package com.tfp.timetracking.timetracking.application;

import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetTenantWorkdayUseCase {

    private final WorkdayRepository workdayRepository;
    private final TenantContext tenantContext;

    public GetTenantWorkdayUseCase(WorkdayRepository workdayRepository, TenantContext tenantContext) {
        this.workdayRepository = workdayRepository;
        this.tenantContext = tenantContext;
    }

    public Workday get(UUID workdayId) {
        return workdayRepository.findById(tenantContext.currentTenantId(), workdayId)
                .orElseThrow(() -> new ResourceNotFoundException("Jornada no encontrada"));
    }
}
