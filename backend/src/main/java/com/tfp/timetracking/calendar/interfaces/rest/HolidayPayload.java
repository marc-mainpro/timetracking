package com.tfp.timetracking.calendar.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Festivo de un calendario (RF-CAL-003). La fecha es local
 * ({@code YYYY-MM-DD}), sin zona ni hora: un festivo es un dia del calendario
 * civil, no un instante (RNF-011).
 */
@Schema(description = "Festivo en una fecha local del calendario")
public record HolidayPayload(
        @NotNull @Schema(example = "2026-01-06") LocalDate date,
        @NotBlank @Size(max = 120) @Schema(example = "Reyes") String name) {}
