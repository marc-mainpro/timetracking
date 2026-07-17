package com.tfp.timetracking.timetracking.application;

import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetWorkdayUseCase {

    private final WorkdayRepository workdayRepository;
    private final TenantContext tenantContext;

    public GetWorkdayUseCase(WorkdayRepository workdayRepository, TenantContext tenantContext) {
        this.workdayRepository = workdayRepository;
        this.tenantContext = tenantContext;
    }

    public Workday get(UUID workdayId) {
        Workday workday = workdayRepository.findById(tenantContext.currentTenantId(), workdayId)
                .orElseThrow(() -> new ResourceNotFoundException("Jornada no encontrada"));
        if (!workday.employeeId().equals(tenantContext.currentUserId())) {
            throw new ResourceNotFoundException("Jornada no encontrada");
        }
        return workday;
    }
}
