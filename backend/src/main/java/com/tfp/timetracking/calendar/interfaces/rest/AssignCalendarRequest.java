package com.tfp.timetracking.calendar.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/admin/calendar-assignments} (RF-CAL-006).
 *
 * @param scope {@code TENANT}, {@code TEAM} o {@code EMPLOYEE}
 * @param targetId equipo o empleado; obligatorio salvo en ambito {@code TENANT}.
 *     En ambito {@code TEAM} es un identificador opaco: el sistema no gestiona
 *     equipos (ADR-0013)
 */
@Schema(description = "Asignacion de un calendario a un ambito")
public record AssignCalendarRequest(
        @NotNull UUID calendarId,
        @NotNull @Schema(example = "EMPLOYEE", allowableValues = {"TENANT", "TEAM", "EMPLOYEE"}) String scope,
        @Schema(description = "Equipo o empleado destinatario; null en ambito TENANT") UUID targetId) {}
