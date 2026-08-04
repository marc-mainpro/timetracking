package com.tfp.timetracking.calendar.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Calendario efectivo de un empleado en una fecha (T70-04).
 *
 * @param scope ambito por el que ha ganado la resolucion; permite explicar al
 *     administrador <i>por que</i> rige ese calendario
 * @param source regla del calendario que ha decidido el dia
 *     ({@code WEEKLY_RULE}, {@code HOLIDAY}, {@code SPECIAL_DAY})
 */
@Schema(description = "Calendario que rige para un empleado en una fecha, con el dia ya evaluado")
public record EffectiveCalendarResponse(
        UUID calendarId,
        String calendarName,
        String timezone,
        UUID assignmentId,
        String scope,
        UUID targetId,
        LocalDate date,
        boolean working,
        int expectedMinutes,
        String source) {}
