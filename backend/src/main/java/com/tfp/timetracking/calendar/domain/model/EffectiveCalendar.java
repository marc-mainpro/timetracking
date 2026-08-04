package com.tfp.timetracking.calendar.domain.model;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Calendario que rige para un empleado en una fecha local concreta, junto con la
 * asignacion que lo ha hecho ganar y el resultado ya evaluado de ese dia.
 *
 * <p>Es el <b>tipo de retorno del contrato de resolucion por ambito</b> que
 * consumiran turnos (T90) y ausencias (T80): con el saben que calendario aplica,
 * por que ambito, si el dia es laborable y cuantos minutos se esperan, sin tener
 * que conocer la estructura interna del calendario ni reimplementar la
 * precedencia.
 *
 * @param assignment asignacion ganadora (la mas especifica aplicable)
 * @param calendar calendario referenciado por esa asignacion
 * @param day evaluacion del calendario sobre la fecha consultada
 */
public record EffectiveCalendar(CalendarAssignment assignment, WorkCalendar calendar, CalendarDay day) {

    public EffectiveCalendar {
        Objects.requireNonNull(assignment, "assignment no puede ser null");
        Objects.requireNonNull(calendar, "calendar no puede ser null");
        Objects.requireNonNull(day, "day no puede ser null");
    }

    /** Ambito por el que se ha resuelto (empleado, equipo o tenant). */
    public AssignmentScope scope() {
        return assignment.scope();
    }

    /** Fecha local evaluada. */
    public LocalDate date() {
        return day.date();
    }

    /** {@code true} si la fecha es laborable segun el calendario ganador. */
    public boolean working() {
        return day.working();
    }

    /** Jornada esperada ese dia; {@link Duration#ZERO} si no es laborable. */
    public Duration expectedHours() {
        return day.expectedDuration();
    }
}
