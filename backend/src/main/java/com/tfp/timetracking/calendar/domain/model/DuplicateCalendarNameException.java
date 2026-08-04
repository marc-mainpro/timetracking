package com.tfp.timetracking.calendar.domain.model;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * Ya existe un calendario con ese nombre en el tenant (RF-CAL-001). Dos
 * calendarios homonimos son indistinguibles en la pantalla de asignacion, que es
 * justo donde un error se paga caro. Codigo estable
 * {@code CALENDAR_NAME_ALREADY_EXISTS}.
 */
public class DuplicateCalendarNameException extends DomainException {

    public DuplicateCalendarNameException() {
        super("CALENDAR_NAME_ALREADY_EXISTS", "Ya existe un calendario con ese nombre");
    }
}
