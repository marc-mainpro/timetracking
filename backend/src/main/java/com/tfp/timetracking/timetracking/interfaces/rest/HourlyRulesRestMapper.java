package com.tfp.timetracking.timetracking.interfaces.rest;

import com.tfp.timetracking.timetracking.domain.HourlyRules;
import org.springframework.stereotype.Component;

@Component
public class HourlyRulesRestMapper {

    public HourlyRulesResponse toResponse(HourlyRules rules) {
        return new HourlyRulesResponse(
                rules.maxDailyWork() != null ? Math.toIntExact(rules.maxDailyWork().toMinutes()) : null,
                rules.requiredBreak() != null ? Math.toIntExact(rules.requiredBreak().toMinutes()) : null);
    }
}
