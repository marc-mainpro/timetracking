package com.tfp.timetracking.calendar.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/** Fila del listado de calendarios. */
@Schema(description = "Resumen de un calendario laboral")
public record CalendarSummaryResponse(
        UUID id,
        String name,
        String timezone,
        LocalDate validFrom,
        LocalDate validTo,
        String status,
        int holidayCount,
        int specialDayCount) {}
