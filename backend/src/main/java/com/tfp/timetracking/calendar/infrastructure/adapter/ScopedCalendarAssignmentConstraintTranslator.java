package com.tfp.timetracking.calendar.infrastructure.adapter;

import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignmentAlreadyExistsException;
import com.tfp.timetracking.shared.application.ConstraintViolationTranslator;
import com.tfp.timetracking.shared.domain.DomainException;
import org.springframework.stereotype.Component;

/**
 * Traduce la violacion de {@code ux_calendar_assignment_scoped} (migracion V16)
 * al error de negocio {@code CALENDAR_ASSIGNMENT_ALREADY_EXISTS}.
 *
 * <p>Cubre la carrera que el chequeo previo del caso de uso no puede evitar: dos
 * peticiones concurrentes asignan a la vez el mismo equipo o empleado, ambas
 * pasan la comprobacion de duplicado y una choca contra el indice unico. Sin
 * esta traduccion el cliente recibiria un {@code CONCURRENT_MODIFICATION}
 * generico en vez del codigo estable de negocio.
 *
 * <p>El ambito exacto no se puede deducir del nombre del indice (cubre
 * {@code TEAM} y {@code EMPLOYEE}), asi que el mensaje usa {@code EMPLOYEE} como
 * representante del caso "asignacion con destinatario"; el {@code errorCode}, que
 * es lo que consume el cliente, es identico en ambos casos.
 */
@Component
public class ScopedCalendarAssignmentConstraintTranslator implements ConstraintViolationTranslator {

    @Override
    public String constraintName() {
        return "ux_calendar_assignment_scoped";
    }

    @Override
    public DomainException translate() {
        return new CalendarAssignmentAlreadyExistsException(AssignmentScope.EMPLOYEE);
    }
}
