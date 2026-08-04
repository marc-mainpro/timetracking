package com.tfp.timetracking.calendar.domain.model;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Resultado de evaluar un {@link WorkCalendar} sobre una fecha local concreta
 * (diseno §9.3: "determinar si un dia es laborable", "obtener horas esperadas",
 * "aplicar jornadas especiales").
 *
 * <p>Es el tipo que consumiran turnos (T90) y ausencias (T80) para saber cuanto
 * "vale" un dia sin conocer la estructura interna del calendario.
 *
 * @param date fecha local evaluada
 * @param working si el dia es laborable
 * @param expectedMinutes minutos de jornada esperados ({@code 0} si no es laborable)
 * @param source regla que ha decidido el resultado
 */
public record CalendarDay(LocalDate date, boolean working, int expectedMinutes, DaySource source) {

    public CalendarDay {
        Objects.requireNonNull(date, "date no puede ser null");
        Objects.requireNonNull(source, "source no puede ser null");
        if (!working && expectedMinutes != 0) {
            throw new IllegalArgumentException("Un dia no laborable no puede tener minutos esperados");
        }
        if (expectedMinutes < 0) {
            throw new IllegalArgumentException("Los minutos esperados no pueden ser negativos");
        }
    }

    /** Jornada esperada como {@link Duration}; {@link Duration#ZERO} si no es laborable. */
    public Duration expectedDuration() {
        return Duration.ofMinutes(expectedMinutes);
    }
}
