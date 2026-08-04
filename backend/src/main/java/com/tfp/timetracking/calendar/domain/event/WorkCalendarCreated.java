package com.tfp.timetracking.calendar.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Hecho pasado: se ha creado un calendario laboral (RF-CAL-001). Se traduce a
 * {@code calendar.calendar-created.v1}.
 *
 * <p>{@code validFrom}/{@code validTo} son {@link LocalDate}: la vigencia de un
 * calendario es un periodo del calendario civil, no un intervalo de instantes
 * (RNF-011). {@code occurredAt} si es un instante UTC: es cuando ocurrio el
 * hecho de administracion.
 */
public record WorkCalendarCreated(
        UUID eventId,
        Instant occurredAt,
        UUID tenantId,
        UUID aggregateId,
        String name,
        String timezone,
        LocalDate validFrom,
        LocalDate validTo) {}
