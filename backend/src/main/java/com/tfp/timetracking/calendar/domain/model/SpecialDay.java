package com.tfp.timetracking.calendar.domain.model;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Jornada especial de un {@link WorkCalendar} (RF-CAL-004, T70-01): una fecha
 * concreta cuya jornada esperada <b>sustituye</b> a la de la regla semanal.
 *
 * <p>Cubre los dos sentidos:
 * <ul>
 *   <li>{@code expectedMinutes > 0} sobre un dia que la regla semanal marca como
 *       laborable: jornada reducida o ampliada (p.ej. jornada intensiva del 24
 *       de diciembre).</li>
 *   <li>{@code expectedMinutes > 0} sobre un dia que la regla semanal marca como
 *       no laborable: dia trabajado excepcionalmente (p.ej. un sabado de
 *       inventario).</li>
 *   <li>{@code expectedMinutes == 0}: dia no laborable puntual que no es un
 *       festivo oficial (p.ej. un "puente" de empresa).</li>
 * </ul>
 *
 * <p>Como {@link Holiday}, es un objeto de valor identificado por su fecha local
 * dentro del calendario; una misma fecha no puede ser a la vez festivo y jornada
 * especial (lo valida {@link WorkCalendar}).
 *
 * @param date fecha local afectada
 * @param name descripcion de la excepcion
 * @param expectedMinutes minutos de jornada esperados ese dia; {@code 0} lo
 *     convierte en no laborable
 */
public record SpecialDay(LocalDate date, String name, int expectedMinutes) {

    /** Longitud maxima del nombre, alineada con la columna de la migracion V16. */
    public static final int MAX_NAME_LENGTH = 120;

    public SpecialDay {
        Objects.requireNonNull(date, "La fecha de la jornada especial es obligatoria");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la jornada especial es obligatorio");
        }
        name = name.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "El nombre de la jornada especial no puede superar " + MAX_NAME_LENGTH + " caracteres");
        }
        if (expectedMinutes < 0) {
            throw new IllegalArgumentException("Los minutos esperados no pueden ser negativos");
        }
        if (expectedMinutes > CalendarDayRule.MAX_EXPECTED_MINUTES) {
            throw new IllegalArgumentException(
                    "Los minutos esperados no pueden superar " + CalendarDayRule.MAX_EXPECTED_MINUTES);
        }
    }

    /** {@code true} si la jornada especial deja el dia como laborable. */
    public boolean working() {
        return expectedMinutes > 0;
    }

    public Duration expectedDuration() {
        return Duration.ofMinutes(expectedMinutes);
    }
}
