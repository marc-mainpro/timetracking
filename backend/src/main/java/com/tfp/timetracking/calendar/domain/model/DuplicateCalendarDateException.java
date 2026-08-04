package com.tfp.timetracking.calendar.domain.model;

import com.tfp.timetracking.shared.domain.DomainException;
import java.time.LocalDate;

/**
 * Una misma fecha local aparece dos veces como festivo, dos veces como jornada
 * especial, o a la vez como festivo y jornada especial (RF-CAL-003/004). El
 * calendario exige una unica excepcion por fecha para que la precedencia sea
 * determinista. Codigo estable {@code CALENDAR_DUPLICATE_DATE}.
 */
public class DuplicateCalendarDateException extends DomainException {

    public DuplicateCalendarDateException(LocalDate date) {
        super("CALENDAR_DUPLICATE_DATE", "La fecha " + date + " ya tiene una excepcion en el calendario");
    }
}
