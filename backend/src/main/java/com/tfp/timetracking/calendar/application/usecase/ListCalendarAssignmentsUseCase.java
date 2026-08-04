package com.tfp.timetracking.calendar.application.usecase;

import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;
import com.tfp.timetracking.calendar.domain.repository.CalendarAssignmentRepository;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Listado paginado de asignaciones del tenant, con filtro opcional por calendario (T70-04). */
@Service
public class ListCalendarAssignmentsUseCase {

    private final CalendarAssignmentRepository assignmentRepository;
    private final TenantContext tenantContext;

    public ListCalendarAssignmentsUseCase(
            CalendarAssignmentRepository assignmentRepository, TenantContext tenantContext) {
        this.assignmentRepository = assignmentRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public PagedResult<CalendarAssignment> list(UUID calendarId, int page, int size) {
        return assignmentRepository.findByTenant(tenantContext.currentTenantId(), calendarId, page, size);
    }
}
