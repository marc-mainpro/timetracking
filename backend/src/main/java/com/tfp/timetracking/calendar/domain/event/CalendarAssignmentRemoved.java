package com.tfp.timetracking.calendar.domain.event;

import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import java.time.Instant;
import java.util.UUID;

/**
 * Hecho pasado: se ha retirado una asignacion de calendario (RF-CAL-006). Se
 * traduce a {@code calendar.calendar-assignment-removed.v1}. Es relevante para
 * turnos y ausencias, porque a partir de ese momento el empleado afectado puede
 * resolver a un calendario menos especifico (o a ninguno).
 */
public record CalendarAssignmentRemoved(
        UUID eventId,
        Instant occurredAt,
        UUID tenantId,
        UUID aggregateId,
        UUID calendarId,
        AssignmentScope scope,
        UUID targetId) {}
