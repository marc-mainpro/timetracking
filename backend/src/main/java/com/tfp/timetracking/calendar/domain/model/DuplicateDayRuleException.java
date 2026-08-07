package com.tfp.timetracking.calendar.domain.model;

import com.tfp.timetracking.shared.domain.DomainException;
import java.time.DayOfWeek;

/**
 * Se han enviado dos reglas semanales para el mismo dia de la semana
 * (RF-CAL-002). Codigo estable {@code CALENDAR_DUPLICATE_DAY_RULE}.
 */
public class DuplicateDayRuleException extends DomainException {

    public DuplicateDayRuleException(DayOfWeek dayOfWeek) {
        super("CALENDAR_DUPLICATE_DAY_RULE", "Ya existe una regla semanal para " + dayOfWeek);
    }
}
