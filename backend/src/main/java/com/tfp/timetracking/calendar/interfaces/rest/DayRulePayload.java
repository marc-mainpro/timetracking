package com.tfp.timetracking.calendar.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;

/** Regla semanal de un calendario (RF-CAL-002), en peticion y en respuesta. */
@Schema(description = "Regla semanal: si el dia de la semana es laborable y cuantos minutos se esperan")
public record DayRulePayload(
        @NotNull @Schema(example = "MONDAY") DayOfWeek dayOfWeek,
        boolean working,
        @Min(0) @Max(1440) @Schema(example = "480") int expectedMinutes) {}
