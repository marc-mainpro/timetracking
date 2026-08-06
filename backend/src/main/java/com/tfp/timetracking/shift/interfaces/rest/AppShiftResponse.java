package com.tfp.timetracking.shift.interfaces.rest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppShiftResponse(
        UUID assignmentId,
        UUID shiftTemplateId,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        boolean crossesMidnight,
        String plannedDuration,
        String plannedBreakDuration,
        LocalDate validFrom,
        LocalDate validTo) {}
