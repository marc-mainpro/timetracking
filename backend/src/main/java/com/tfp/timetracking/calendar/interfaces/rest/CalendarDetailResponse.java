package com.tfp.timetracking.calendar.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Detalle completo de un calendario laboral, incluidas sus reglas y excepciones. */
@Schema(description = "Detalle de un calendario laboral")
public record CalendarDetailResponse(
        UUID id,
        String name,
        String timezone,
        LocalDate validFrom,
        LocalDate validTo,
        String status,
        List<DayRulePayload> dayRules,
        List<HolidayPayload> holidays,
        List<SpecialDayPayload> specialDays,
        long version,
        Instant createdAt,
        Instant updatedAt) {}
