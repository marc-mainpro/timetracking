package com.tfp.timetracking.calendar.domain.event;

import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import java.time.Instant;
import java.util.UUID;

/**
 * Hecho pasado: se ha asignado un calendario a un ambito (RF-CAL-006). Se
 * traduce a {@code calendar.calendar-assigned.v1}.
 *
 * <p>{@code aggregateId} es el id de la asignacion; el calendario afectado va en
 * {@code calendarId}.
 */
public record CalendarAssigned(
        UUID eventId,
        Instant occurredAt,
        UUID tenantId,
        UUID aggregateId,
        UUID calendarId,
        AssignmentScope scope,
        UUID targetId) {}
