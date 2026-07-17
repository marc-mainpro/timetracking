package com.tfp.timetracking.timetracking.application;

import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ListOwnWorkdaysUseCase {

    private final WorkdayRepository workdayRepository;
    private final TenantContext tenantContext;

    public ListOwnWorkdaysUseCase(WorkdayRepository workdayRepository, TenantContext tenantContext) {
        this.workdayRepository = workdayRepository;
        this.tenantContext = tenantContext;
    }

    public PagedResult<Workday> list(int page, int size, Instant from, Instant to) {
        return workdayRepository.findByEmployee(
                tenantContext.currentTenantId(), tenantContext.currentUserId(), from, to, page, size);
    }
}
