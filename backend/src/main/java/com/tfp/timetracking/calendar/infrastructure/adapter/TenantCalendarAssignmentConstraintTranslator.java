package com.tfp.timetracking.calendar.infrastructure.adapter;

import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignmentAlreadyExistsException;
import com.tfp.timetracking.shared.application.ConstraintViolationTranslator;
import com.tfp.timetracking.shared.domain.DomainException;
import org.springframework.stereotype.Component;

/**
 * Traduce la violacion de {@code ux_calendar_assignment_tenant_scope}
 * (migracion V16), el indice parcial que garantiza una unica asignacion de
 * ambito {@code TENANT} por organizacion, al error de negocio
 * {@code CALENDAR_ASSIGNMENT_ALREADY_EXISTS}.
 */
@Component
public class TenantCalendarAssignmentConstraintTranslator implements ConstraintViolationTranslator {

    @Override
    public String constraintName() {
        return "ux_calendar_assignment_tenant_scope";
    }

    @Override
    public DomainException translate() {
        return new CalendarAssignmentAlreadyExistsException(AssignmentScope.TENANT);
    }
}
