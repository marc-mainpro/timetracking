package com.tfp.timetracking.calendar.application.dto;

import com.tfp.timetracking.calendar.domain.model.DaySource;
import com.tfp.timetracking.calendar.domain.model.EffectiveCalendar;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Proyeccion plana del calendario efectivo de un empleado en una fecha, lista
 * para cruzar el borde de la aplicacion (API, y en el futuro turnos y ausencias
 * si prefieren un tipo sin agregados dentro).
 *
 * <p>Se ofrece ademas de {@link EffectiveCalendar} porque este ultimo arrastra
 * los agregados completos: util dentro del dominio, excesivo para una respuesta.
 */
public record EffectiveCalendarView(
        UUID calendarId,
        String calendarName,
        String timezone,
        UUID assignmentId,
        String scope,
        UUID targetId,
        LocalDate date,
        boolean working,
        int expectedMinutes,
        DaySource source) {

    public static EffectiveCalendarView from(EffectiveCalendar effective) {
        return new EffectiveCalendarView(
                effective.calendar().id(),
                effective.calendar().name(),
                effective.calendar().timezone(),
                effective.assignment().id(),
                effective.scope().name(),
                effective.assignment().targetId(),
                effective.date(),
                effective.working(),
                effective.day().expectedMinutes(),
                effective.day().source());
    }
}
