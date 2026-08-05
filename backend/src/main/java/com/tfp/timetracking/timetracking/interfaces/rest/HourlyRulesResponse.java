package com.tfp.timetracking.timetracking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Reglas horarias efectivas del tenant")
public record HourlyRulesResponse(
        @Schema(description = "Maximo de minutos trabajados por jornada", example = "480") Integer maxDailyWorkMinutes,
        @Schema(description = "Minimo de minutos de pausa requeridos", example = "30") Integer requiredBreakMinutes,
        @Schema(description = "Paso de redondeo en minutos", example = "15") Integer roundingStepMinutes,
        @Schema(description = "Tolerancia en minutos", example = "5") Integer toleranceMinutes) {}
