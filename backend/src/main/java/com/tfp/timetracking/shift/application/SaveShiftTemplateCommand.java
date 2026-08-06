package com.tfp.timetracking.shift.application;

import java.time.LocalTime;

public record SaveShiftTemplateCommand(
        String name, LocalTime startTime, LocalTime endTime, Integer plannedBreakMinutes) {}
