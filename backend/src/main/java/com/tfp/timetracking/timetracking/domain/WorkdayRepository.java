package com.tfp.timetracking.timetracking.domain;

import com.tfp.timetracking.shared.domain.PagedResult;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WorkdayRepository {

    Workday save(Workday workday);

    Optional<Workday> findById(UUID tenantId, UUID id);

    Optional<Workday> findActiveByEmployee(UUID tenantId, UUID employeeId);

    PagedResult<Workday> findByEmployee(UUID tenantId, UUID employeeId, Instant from, Instant to, int page, int size);

    PagedResult<Workday> findByTenant(UUID tenantId, UUID employeeId, Instant from, Instant to, int page, int size);
}
