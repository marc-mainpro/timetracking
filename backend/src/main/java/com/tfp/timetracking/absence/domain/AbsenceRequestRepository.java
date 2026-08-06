package com.tfp.timetracking.absence.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AbsenceRequestRepository {

    AbsenceRequest save(AbsenceRequest request);

    Optional<AbsenceRequest> findById(UUID tenantId, UUID requestId);

    List<AbsenceRequest> findByEmployeeAndDateRange(UUID tenantId, UUID employeeId, LocalDate from, LocalDate to);

    List<AbsenceRequest> findApprovedByEmployeeAndDateRange(UUID tenantId, UUID employeeId, LocalDate from, LocalDate to);

    List<AbsenceRequest> findByTenantAndDateRange(UUID tenantId, LocalDate from, LocalDate to);
}
