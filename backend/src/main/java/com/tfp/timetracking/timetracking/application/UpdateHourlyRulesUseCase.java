package com.tfp.timetracking.timetracking.application;

import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.timetracking.domain.HourlyRules;
import com.tfp.timetracking.timetracking.domain.HourlyRulesRepository;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateHourlyRulesUseCase {

    private final HourlyRulesRepository hourlyRulesRepository;
    private final TenantContext tenantContext;

    public UpdateHourlyRulesUseCase(HourlyRulesRepository hourlyRulesRepository, TenantContext tenantContext) {
        this.hourlyRulesRepository = hourlyRulesRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public HourlyRules update(Integer maxDailyWorkMinutes, Integer requiredBreakMinutes) {
        Duration maxDailyWork = maxDailyWorkMinutes != null ? Duration.ofMinutes(maxDailyWorkMinutes) : null;
        Duration requiredBreak = requiredBreakMinutes != null ? Duration.ofMinutes(requiredBreakMinutes) : null;
        return hourlyRulesRepository.save(new HourlyRules(tenantContext.currentTenantId(), maxDailyWork, requiredBreak));
    }
}
