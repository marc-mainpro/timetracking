package com.tfp.timetracking.shift.interfaces.rest;

import java.time.LocalTime;
import java.util.UUID;

public record ShiftTemplateResponse(
        UUID id,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        Integer plannedBreakMinutes,
        String status,
        boolean crossesMidnight) {}
