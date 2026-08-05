package com.tfp.timetracking.timetracking.interfaces.rest;

import java.time.Duration;
import java.util.List;

public record WorkdayEvaluationResponse(
        Duration expectedDuration,
        Duration workedDuration,
        Duration effectiveWorkedDuration,
        Duration pausedDuration,
        Duration overtimeDuration,
        Duration deviationDuration,
        List<String> anomalies) {}
