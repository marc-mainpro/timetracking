package com.tfp.timetracking.timetracking.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

public record BreakEntryResponse(UUID id, Instant startedAt, Instant endedAt) {}
