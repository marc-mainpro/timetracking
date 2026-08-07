package com.tfp.timetracking.calendar.application.command;

import java.time.LocalDate;
import java.util.List;

/**
 * Estado completo deseado de un calendario laboral, usado tanto al crear
 * (RF-CAL-001) como al editar (T70-04).
 *
 * <p>Las tres colecciones son <b>sustituciones completas</b>, no deltas: el
 * cliente envia el calendario tal y como debe quedar. Es la semantica de un
 * {@code PUT} y evita una API de parcheo con operaciones por elemento que
 * complicaria la concurrencia sin aportar nada al caso de uso real (editar el
 * calendario en un formulario).
 *
 * <p>El {@code tenantId} <b>no</b> viaja en el comando: se resuelve del
 * principal autenticado en el caso de uso (AGENTS.md, "nunca confies en el
 * tenant que envia el cliente").
 */
public record SaveWorkCalendarCommand(
        String name,
        String timezone,
        LocalDate validFrom,
        LocalDate validTo,
        List<DayRuleCommand> dayRules,
        List<HolidayCommand> holidays,
        List<SpecialDayCommand> specialDays) {

    public SaveWorkCalendarCommand {
        dayRules = dayRules == null ? List.of() : List.copyOf(dayRules);
        holidays = holidays == null ? List.of() : List.copyOf(holidays);
        specialDays = specialDays == null ? List.of() : List.copyOf(specialDays);
    }
}
