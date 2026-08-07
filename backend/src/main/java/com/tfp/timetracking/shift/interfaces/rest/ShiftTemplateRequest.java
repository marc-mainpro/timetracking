package com.tfp.timetracking.shift.interfaces.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record ShiftTemplateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @Min(0) @Max(1440) Integer plannedBreakMinutes) {}
