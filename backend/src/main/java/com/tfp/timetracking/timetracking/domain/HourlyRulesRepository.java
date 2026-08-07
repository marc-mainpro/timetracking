package com.tfp.timetracking.timetracking.domain;

import java.util.Optional;
import java.util.UUID;

public interface HourlyRulesRepository {

    HourlyRules save(HourlyRules rules);

    Optional<HourlyRules> findByTenantId(UUID tenantId);
}
