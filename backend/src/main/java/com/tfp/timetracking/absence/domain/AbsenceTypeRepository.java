package com.tfp.timetracking.absence.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AbsenceTypeRepository {

    AbsenceType save(AbsenceType absenceType);

    Optional<AbsenceType> findById(UUID tenantId, UUID absenceTypeId);

    Optional<AbsenceType> findByCode(UUID tenantId, String code);

    List<AbsenceType> findByTenantId(UUID tenantId);
}
