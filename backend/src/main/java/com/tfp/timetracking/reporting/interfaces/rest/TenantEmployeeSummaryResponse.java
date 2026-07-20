package com.tfp.timetracking.reporting.interfaces.rest;

import java.time.Duration;
import java.util.UUID;

public record TenantEmployeeSummaryResponse(
        UUID employeeId, Duration worked, Duration paused, int workdayCount, int adjustedWorkdayCount, int openWorkdays) {}
