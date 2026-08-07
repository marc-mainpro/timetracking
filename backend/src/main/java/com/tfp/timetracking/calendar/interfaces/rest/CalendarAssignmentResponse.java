package com.tfp.timetracking.calendar.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Asignacion de un calendario a un ambito. */
@Schema(description = "Asignacion de calendario")
public record CalendarAssignmentResponse(
        UUID id, UUID calendarId, String scope, UUID targetId, Instant createdAt) {}
