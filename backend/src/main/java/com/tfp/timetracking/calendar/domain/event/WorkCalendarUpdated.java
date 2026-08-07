package com.tfp.timetracking.calendar.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Hecho pasado: se ha editado un calendario laboral (T70-04). Se traduce a
 * {@code calendar.calendar-updated.v1}.
 *
 * <p>El payload lleva la cabecera del calendario, no el detalle de reglas,
 * festivos y jornadas especiales: un consumidor que necesite el detalle debe
 * releerlo por la API. Mantener el contrato externo pequeno evita versionarlo
 * cada vez que cambie la estructura interna del calendario.
 */
public record WorkCalendarUpdated(
        UUID eventId,
        Instant occurredAt,
        UUID tenantId,
        UUID aggregateId,
        String name,
        String timezone,
        LocalDate validFrom,
        LocalDate validTo) {}
