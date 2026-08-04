package com.tfp.timetracking.calendar.infrastructure.adapter;

import com.tfp.timetracking.calendar.domain.model.DuplicateCalendarNameException;
import com.tfp.timetracking.shared.application.ConstraintViolationTranslator;
import com.tfp.timetracking.shared.domain.DomainException;
import org.springframework.stereotype.Component;

/**
 * Traduce la violacion de {@code ux_work_calendar_tenant_name} (migracion V16)
 * al error de negocio {@code CALENDAR_NAME_ALREADY_EXISTS}, en vez de dejar que
 * escale como conflicto de concurrencia generico.
 */
@Component
public class DuplicateCalendarNameConstraintTranslator implements ConstraintViolationTranslator {

    @Override
    public String constraintName() {
        return "ux_work_calendar_tenant_name";
    }

    @Override
    public DomainException translate() {
        return new DuplicateCalendarNameException();
    }
}
