package com.tfp.timetracking.timetracking.application;

import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.timetracking.domain.HourlyRules;
import com.tfp.timetracking.timetracking.domain.HourlyRulesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetHourlyRulesUseCase {

    private final HourlyRulesRepository hourlyRulesRepository;
    private final TenantContext tenantContext;

    public GetHourlyRulesUseCase(HourlyRulesRepository hourlyRulesRepository, TenantContext tenantContext) {
        this.hourlyRulesRepository = hourlyRulesRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public HourlyRules get() {
        return hourlyRulesRepository.findByTenantId(tenantContext.currentTenantId())
                .orElse(HourlyRules.withoutLimits(tenantContext.currentTenantId()));
    }
}
