package com.tfp.timetracking.calendar.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Hecho pasado: se ha archivado un calendario laboral (T70-04). Se traduce a
 * {@code calendar.calendar-archived.v1}. Un calendario archivado deja de
 * participar en la resolucion del calendario efectivo.
 */
public record WorkCalendarArchived(
        UUID eventId, Instant occurredAt, UUID tenantId, UUID aggregateId, String name) {}
