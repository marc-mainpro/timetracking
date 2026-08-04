package com.tfp.timetracking.calendar.application.usecase;

import com.tfp.timetracking.calendar.domain.model.CalendarStatus;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.calendar.domain.repository.WorkCalendarRepository;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Listado paginado de calendarios del tenant, con filtro opcional por estado (T70-04). */
@Service
public class ListWorkCalendarsUseCase {

    private final WorkCalendarRepository calendarRepository;
    private final TenantContext tenantContext;

    public ListWorkCalendarsUseCase(WorkCalendarRepository calendarRepository, TenantContext tenantContext) {
        this.calendarRepository = calendarRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public PagedResult<WorkCalendar> list(CalendarStatus status, int page, int size) {
        return calendarRepository.findByTenant(tenantContext.currentTenantId(), status, page, size);
    }
}
