package com.tfp.timetracking.timetracking.application;

import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ListTenantWorkdaysUseCase {

    private final WorkdayRepository workdayRepository;
    private final TenantContext tenantContext;

    public ListTenantWorkdaysUseCase(WorkdayRepository workdayRepository, TenantContext tenantContext) {
        this.workdayRepository = workdayRepository;
        this.tenantContext = tenantContext;
    }

    public PagedResult<Workday> list(int page, int size, UUID employeeId, Instant from, Instant to) {
        return workdayRepository.findByTenant(tenantContext.currentTenantId(), employeeId, from, to, page, size);
    }
}
