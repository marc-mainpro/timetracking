package com.tfp.timetracking.calendar.domain.model;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * Ya existe una asignacion para ese ambito y destinatario (RF-CAL-006). Solo
 * puede haber una asignacion vigente por (tenant, ambito, destinatario): dos
 * asignaciones del mismo ambito harian ambigua la precedencia. Codigo estable
 * {@code CALENDAR_ASSIGNMENT_ALREADY_EXISTS}.
 */
public class CalendarAssignmentAlreadyExistsException extends DomainException {

    public CalendarAssignmentAlreadyExistsException(AssignmentScope scope) {
        super(
                "CALENDAR_ASSIGNMENT_ALREADY_EXISTS",
                "Ya existe una asignacion de calendario para el ambito " + scope);
    }
}
