package com.tfp.timetracking.timetracking.interfaces.rest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkdayResponse(
        UUID id,
        String status,
        Instant startedAt,
        Instant endedAt,
        List<BreakEntryResponse> breaks,
        Duration workedDuration) {}
