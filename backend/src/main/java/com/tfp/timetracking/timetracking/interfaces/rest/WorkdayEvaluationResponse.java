package com.tfp.timetracking.timetracking.interfaces.rest;

import java.time.Duration;
import java.util.List;

public record WorkdayEvaluationResponse(
        Duration expectedDuration,
        Duration workedDuration,
        Duration pausedDuration,
        Duration overtimeDuration,
        List<String> anomalies) {}
