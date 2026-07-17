package com.tfp.timetracking.timetracking.domain;

import java.util.Optional;
import java.util.UUID;

public interface WorkdayRepository {

    Workday save(Workday workday);

    Optional<Workday> findById(UUID tenantId, UUID id);

    Optional<Workday> findActiveByEmployee(UUID tenantId, UUID employeeId);
}
