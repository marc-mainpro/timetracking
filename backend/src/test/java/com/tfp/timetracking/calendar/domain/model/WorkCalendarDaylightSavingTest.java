package com.tfp.timetracking.calendar.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Cambio de horario de verano (RNF-011 / RNF-012).
 *
 * <p>En {@code Europe/Madrid} el ultimo domingo de marzo el reloj salta de las
 * 02:00 a las 03:00 y el dia civil dura <b>23 h</b>; el ultimo domingo de
 * octubre las 03:00 vuelven a las 02:00 y el dia dura <b>25 h</b>. Estas pruebas
 * fijan las dos consecuencias que importan al calendario:
 *
 * <ol>
 *   <li>La jornada esperada de un dia <b>no</b> cambia porque el dia civil dure
 *       23 o 25 horas: es una regla del calendario laboral, no una medida de la
 *       linea temporal. Un lunes de 8 h sigue siendo de 8 h.</li>
 *   <li>La conversion fecha local -&gt; instante UTC, que ocurre solo en los
 *       bordes, si refleja la duracion real del dia. Quien calcule solapes o
 *       ventanas debe usar {@code startOfDay}/{@code endOfDayExclusive} y no
 *       sumar 24 h a mano.</li>
 * </ol>
 */
class WorkCalendarDaylightSavingTest {

    /** 2026-03-29: adelanto de hora en Europe/Madrid; el dia dura 23 h. */
    private static final LocalDate SPRING_FORWARD = LocalDate.of(2026, 3, 29);

    /** 2026-10-25: atraso de hora en Europe/Madrid; el dia dura 25 h. */
    private static final LocalDate FALL_BACK = LocalDate.of(2026, 10, 25);

    private final Clock clock = () -> Instant.parse("2026-01-01T00:00:00Z");
    private final IdGenerator idGenerator = UUID::randomUUID;

    private WorkCalendar madridCalendar() {
        return WorkCalendar.create(
                UUID.randomUUID(),
                "Madrid",
                "Europe/Madrid",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                List.of(
                        CalendarDayRule.working(DayOfWeek.MONDAY, 480),
                        CalendarDayRule.working(DayOfWeek.SUNDAY, 480)),
                List.of(),
                List.of(),
                clock,
                idGenerator);
    }

    @Test
    void springForwardDayLastsTwentyThreeHours() {
        WorkCalendar calendar = madridCalendar();

        assertThat(calendar.civilDayLength(SPRING_FORWARD)).isEqualTo(Duration.ofHours(23));
        assertThat(Duration.between(
                        calendar.startOfDay(SPRING_FORWARD), calendar.endOfDayExclusive(SPRING_FORWARD)))
                .isEqualTo(Duration.ofHours(23));
        // El instante inicial es medianoche local, todavia en horario de invierno (UTC+1).
        assertThat(calendar.startOfDay(SPRING_FORWARD)).isEqualTo(Instant.parse("2026-03-28T23:00:00Z"));
        assertThat(calendar.endOfDayExclusive(SPRING_FORWARD)).isEqualTo(Instant.parse("2026-03-29T22:00:00Z"));
    }

    @Test
    void fallBackDayLastsTwentyFiveHours() {
        WorkCalendar calendar = madridCalendar();

        assertThat(calendar.civilDayLength(FALL_BACK)).isEqualTo(Duration.ofHours(25));
        assertThat(calendar.startOfDay(FALL_BACK)).isEqualTo(Instant.parse("2026-10-24T22:00:00Z"));
        assertThat(calendar.endOfDayExclusive(FALL_BACK)).isEqualTo(Instant.parse("2026-10-25T23:00:00Z"));
    }

    @Test
    void expectedHoursAreUnaffectedByDaylightSavingTransitions() {
        WorkCalendar calendar = madridCalendar();

        // Ambas fechas son domingo y ambas tienen la misma jornada esperada,
        // aunque una dure 23 h y la otra 25 h.
        assertThat(SPRING_FORWARD.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(FALL_BACK.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);

        assertThat(calendar.expectedHours(SPRING_FORWARD)).isEqualTo(Duration.ofHours(8));
        assertThat(calendar.expectedHours(FALL_BACK)).isEqualTo(Duration.ofHours(8));
        assertThat(calendar.isWorkingDay(SPRING_FORWARD)).isTrue();
        assertThat(calendar.isWorkingDay(FALL_BACK)).isTrue();
    }

    @Test
    void ordinaryDayStillLastsTwentyFourHours() {
        WorkCalendar calendar = madridCalendar();

        assertThat(calendar.civilDayLength(LocalDate.of(2026, 3, 30))).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void holidayOnDaylightSavingDayIsStillANonWorkingLocalDate() {
        WorkCalendar calendar = WorkCalendar.create(
                UUID.randomUUID(),
                "Madrid",
                "Europe/Madrid",
                LocalDate.of(2026, 1, 1),
                null,
                List.of(CalendarDayRule.working(DayOfWeek.SUNDAY, 480)),
                List.of(new Holiday(FALL_BACK, "Festivo ficticio")),
                List.of(),
                clock,
                idGenerator);

        // Un festivo es un dia del calendario civil: que ese dia dure 25 h no lo
        // convierte en parcialmente laborable.
        assertThat(calendar.isWorkingDay(FALL_BACK)).isFalse();
        assertThat(calendar.dayOf(FALL_BACK).source()).isEqualTo(DaySource.HOLIDAY);
        assertThat(calendar.civilDayLength(FALL_BACK)).isEqualTo(Duration.ofHours(25));
    }

    @Test
    void sameLocalDateMapsToDifferentInstantsInDifferentZones() {
        // RNF-012: la zona del calendario decide el instante en que empieza el
        // dia. La misma fecha local no es el mismo punto de la linea temporal.
        WorkCalendar madrid = madridCalendar();
        WorkCalendar canary = WorkCalendar.create(
                UUID.randomUUID(),
                "Canarias",
                "Atlantic/Canary",
                LocalDate.of(2026, 1, 1),
                null,
                List.of(CalendarDayRule.working(DayOfWeek.MONDAY, 480)),
                List.of(),
                List.of(),
                clock,
                idGenerator);

        LocalDate date = LocalDate.of(2026, 7, 6);
        assertThat(Duration.between(madrid.startOfDay(date), canary.startOfDay(date)))
                .isEqualTo(Duration.ofHours(1));
    }
}
