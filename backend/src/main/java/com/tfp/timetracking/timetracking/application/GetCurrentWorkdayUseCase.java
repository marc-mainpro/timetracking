package com.tfp.timetracking.timetracking.application;

import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import org.springframework.stereotype.Service;

@Service
public class GetCurrentWorkdayUseCase {

    private final WorkdayRepository workdayRepository;
    private final TenantContext tenantContext;

    public GetCurrentWorkdayUseCase(WorkdayRepository workdayRepository, TenantContext tenantContext) {
        this.workdayRepository = workdayRepository;
        this.tenantContext = tenantContext;
    }

    public Workday get() {
        return workdayRepository.findActiveByEmployee(tenantContext.currentTenantId(), tenantContext.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("No existe jornada activa"));
    }
}
