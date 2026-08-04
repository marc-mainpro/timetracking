package com.tfp.timetracking.calendar.application.usecase;

import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.calendar.domain.repository.WorkCalendarRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta el detalle de un calendario (T70-04). Un calendario de otro tenant
 * produce 404, nunca 403 (ADR-0002): responder 403 revelaria que el recurso
 * existe.
 */
@Service
public class GetWorkCalendarUseCase {

    private final WorkCalendarRepository calendarRepository;
    private final TenantContext tenantContext;

    public GetWorkCalendarUseCase(WorkCalendarRepository calendarRepository, TenantContext tenantContext) {
        this.calendarRepository = calendarRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public WorkCalendar get(UUID calendarId) {
        return calendarRepository
                .findById(tenantContext.currentTenantId(), calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendario no encontrado"));
    }
}
