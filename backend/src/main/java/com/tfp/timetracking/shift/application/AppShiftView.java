package com.tfp.timetracking.shift.application;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppShiftView(
        UUID assignmentId,
        UUID shiftTemplateId,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        boolean crossesMidnight,
        Duration plannedDuration,
        Duration plannedBreakDuration,
        LocalDate validFrom,
        LocalDate validTo) {}
