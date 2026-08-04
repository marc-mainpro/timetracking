package com.tfp.timetracking.calendar.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Jornada especial de un calendario (RF-CAL-004): sustituye la jornada esperada
 * de una fecha concreta. {@code expectedMinutes = 0} la deja no laborable.
 */
@Schema(description = "Jornada especial que sustituye la regla semanal en una fecha concreta")
public record SpecialDayPayload(
        @NotNull @Schema(example = "2026-12-24") LocalDate date,
        @NotBlank @Size(max = 120) @Schema(example = "Jornada intensiva de Nochebuena") String name,
        @Min(0) @Max(1440) @Schema(example = "300") int expectedMinutes) {}
