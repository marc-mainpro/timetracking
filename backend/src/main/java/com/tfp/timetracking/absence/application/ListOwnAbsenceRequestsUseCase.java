package com.tfp.timetracking.absence.application;

import com.tfp.timetracking.absence.domain.AbsenceRequest;
import com.tfp.timetracking.absence.domain.AbsenceRequestRepository;
import com.tfp.timetracking.shared.application.TenantContext;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListOwnAbsenceRequestsUseCase {

    private final AbsenceRequestRepository absenceRequestRepository;
    private final TenantContext tenantContext;

    public ListOwnAbsenceRequestsUseCase(AbsenceRequestRepository absenceRequestRepository, TenantContext tenantContext) {
        this.absenceRequestRepository = absenceRequestRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public List<AbsenceRequest> list(LocalDate from, LocalDate to) {
        return absenceRequestRepository.findByEmployeeAndDateRange(
                tenantContext.currentTenantId(),
                tenantContext.currentUserId(),
                from != null ? from : LocalDate.MIN,
                to != null ? to : LocalDate.MAX);
    }
}
