package com.tfp.timetracking.calendar.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Cuerpo de {@code POST} y {@code PUT} de {@code /api/v1/admin/calendars}
 * (RF-CAL-001..005). Las tres colecciones son el estado completo deseado, no un
 * delta.
 *
 * <p>No lleva {@code tenantId}: el tenant sale del principal autenticado
 * (AGENTS.md, "nunca confies en el tenant que envia el cliente").
 */
@Schema(description = "Estado completo de un calendario laboral")
public record SaveCalendarRequest(
        @NotBlank @Size(max = 120) @Schema(example = "Calendario general") String name,
        @Size(max = 60) @Schema(description = "Zona horaria IANA; por defecto la configurada", example = "Europe/Madrid")
                String timezone,
        @NotNull @Schema(example = "2026-01-01") LocalDate validFrom,
        @Schema(description = "Fin de vigencia inclusivo; null = indefinida", example = "2026-12-31")
                LocalDate validTo,
        @Valid List<DayRulePayload> dayRules,
        @Valid List<HolidayPayload> holidays,
        @Valid List<SpecialDayPayload> specialDays) {}
