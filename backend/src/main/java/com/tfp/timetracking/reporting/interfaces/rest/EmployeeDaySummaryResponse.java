package com.tfp.timetracking.reporting.interfaces.rest;

import java.time.Duration;
import java.time.LocalDate;

public record EmployeeDaySummaryResponse(
        LocalDate day,
        Duration worked,
        Duration paused,
        Duration expected,
        Duration effectiveWorked,
        Duration overtime,
        Duration deviation,
        int workdayCount,
        int adjustedWorkdayCount,
        int openWorkdays,
        int evaluatedWorkdayCount) {}
