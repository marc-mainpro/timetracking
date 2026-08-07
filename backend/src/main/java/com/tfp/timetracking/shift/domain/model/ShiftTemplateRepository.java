package com.tfp.timetracking.shift.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftTemplateRepository {

    ShiftTemplate save(ShiftTemplate template);

    Optional<ShiftTemplate> findById(UUID tenantId, UUID id);

    Optional<ShiftTemplate> findByName(UUID tenantId, String name);

    List<ShiftTemplate> findByTenantId(UUID tenantId);
}
