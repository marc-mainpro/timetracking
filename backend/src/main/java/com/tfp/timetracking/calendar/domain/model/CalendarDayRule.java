package com.tfp.timetracking.calendar.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.Objects;

/**
 * Regla semanal de un {@link WorkCalendar} (RF-CAL-002, T70-01): dice si un dia
 * de la semana es laborable y cuantos minutos de jornada se esperan en el.
 *
 * <p>Es un objeto de valor: se identifica por su {@link #dayOfWeek()} dentro del
 * calendario, no por un id propio. Trabaja siempre en <b>tiempo local</b>
 * (RNF-011/RNF-012): un "lunes" es un dia del calendario civil del tenant, no un
 * instante.
 */
public final class CalendarDayRule {

    /** Cota superior de minutos esperados en un dia (24 h). */
    public static final int MAX_EXPECTED_MINUTES = 24 * 60;

    private final DayOfWeek dayOfWeek;
    private final boolean working;
    private final int expectedMinutes;

    private CalendarDayRule(DayOfWeek dayOfWeek, boolean working, int expectedMinutes) {
        this.dayOfWeek = dayOfWeek;
        this.working = working;
        this.expectedMinutes = expectedMinutes;
    }

    /**
     * Regla laborable con jornada esperada.
     *
     * @throws IllegalArgumentException si los minutos no estan en {@code (0, 1440]}
     */
    public static CalendarDayRule working(DayOfWeek dayOfWeek, int expectedMinutes) {
        Objects.requireNonNull(dayOfWeek, "dayOfWeek no puede ser null");
        if (expectedMinutes <= 0) {
            throw new IllegalArgumentException("Un dia laborable debe tener minutos esperados mayores que 0");
        }
        if (expectedMinutes > MAX_EXPECTED_MINUTES) {
            throw new IllegalArgumentException("Los minutos esperados no pueden superar " + MAX_EXPECTED_MINUTES);
        }
        return new CalendarDayRule(dayOfWeek, true, expectedMinutes);
    }

    /** Regla no laborable (0 minutos esperados). */
    public static CalendarDayRule nonWorking(DayOfWeek dayOfWeek) {
        Objects.requireNonNull(dayOfWeek, "dayOfWeek no puede ser null");
        return new CalendarDayRule(dayOfWeek, false, 0);
    }

    /**
     * Factoria general usada tambien al reconstituir desde persistencia:
     * {@code working=false} implica 0 minutos esperados.
     */
    public static CalendarDayRule of(DayOfWeek dayOfWeek, boolean working, int expectedMinutes) {
        if (!working) {
            if (expectedMinutes != 0) {
                throw new IllegalArgumentException("Un dia no laborable no puede tener minutos esperados");
            }
            return nonWorking(dayOfWeek);
        }
        return working(dayOfWeek, expectedMinutes);
    }

    public DayOfWeek dayOfWeek() {
        return dayOfWeek;
    }

    public boolean working() {
        return working;
    }

    public int expectedMinutes() {
        return expectedMinutes;
    }

    public Duration expectedDuration() {
        return Duration.ofMinutes(expectedMinutes);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CalendarDayRule rule)) {
            return false;
        }
        return working == rule.working && expectedMinutes == rule.expectedMinutes && dayOfWeek == rule.dayOfWeek;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dayOfWeek, working, expectedMinutes);
    }

    @Override
    public String toString() {
        return "CalendarDayRule[" + dayOfWeek + ", working=" + working + ", expectedMinutes=" + expectedMinutes + "]";
    }
}
