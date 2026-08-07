package com.tfp.timetracking.calendar.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Festivo de un {@link WorkCalendar} (RF-CAL-003, T70-01).
 *
 * <p>Es un objeto de valor identificado por su {@link #date()} dentro del
 * calendario. La fecha es una {@link LocalDate} y no un {@code Instant} a
 * proposito (RNF-011/RNF-012): "el 6 de enero es festivo" es un hecho del
 * calendario civil del tenant, no un instante en la linea temporal. La
 * conversion a UTC se hace solo en los bordes, con
 * {@link WorkCalendar#startOfDay(LocalDate)}.
 *
 * @param date fecha local del festivo
 * @param name nombre del festivo (p.ej. "Reyes")
 */
public record Holiday(LocalDate date, String name) {

    /** Longitud maxima del nombre, alineada con la columna de la migracion V16. */
    public static final int MAX_NAME_LENGTH = 120;

    public Holiday {
        Objects.requireNonNull(date, "La fecha del festivo es obligatoria");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del festivo es obligatorio");
        }
        name = name.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("El nombre del festivo no puede superar " + MAX_NAME_LENGTH + " caracteres");
        }
    }
}
