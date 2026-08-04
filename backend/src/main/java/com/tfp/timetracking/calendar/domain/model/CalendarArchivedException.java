package com.tfp.timetracking.calendar.domain.model;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * Se ha intentado editar, archivar o asignar un calendario ya archivado
 * (T70-04). Codigo estable {@code CALENDAR_ARCHIVED}, traducido a 409 por el
 * manejador global (ADR-0006).
 */
public class CalendarArchivedException extends DomainException {

    public CalendarArchivedException() {
        super("CALENDAR_ARCHIVED", "El calendario esta archivado y no admite cambios");
    }
}
