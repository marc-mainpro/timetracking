package com.tfp.timetracking.reporting.interfaces.rest;

import java.time.Duration;
import java.util.UUID;

public record TenantEmployeeSummaryResponse(
        UUID employeeId,
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
