package com.tfp.timetracking.calendar.domain.model;

/** Estado de un {@link WorkCalendar} (T70-04: crear, editar, archivar). */
public enum CalendarStatus {

    /** Editable y elegible en la resolucion del calendario efectivo. */
    ACTIVE,

    /**
     * Archivado: no se puede editar ni asignar, y deja de participar en la
     * resolucion del calendario efectivo. No se borra fisicamente para no
     * romper el historico de jornadas ya calculadas con el.
     */
    ARCHIVED
}
