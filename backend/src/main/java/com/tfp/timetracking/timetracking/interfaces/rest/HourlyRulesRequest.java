package com.tfp.timetracking.timetracking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "Configuracion de reglas horarias del tenant")
public record HourlyRulesRequest(
        @Positive @Schema(description = "Maximo de minutos trabajados por jornada", example = "480") Integer maxDailyWorkMinutes,
        @PositiveOrZero @Schema(description = "Minimo de minutos de pausa requeridos", example = "30") Integer requiredBreakMinutes) {}
