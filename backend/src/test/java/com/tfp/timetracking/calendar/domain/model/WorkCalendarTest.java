package com.tfp.timetracking.calendar.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.tfp.timetracking.calendar.domain.event.WorkCalendarArchived;
import com.tfp.timetracking.calendar.domain.event.WorkCalendarCreated;
import com.tfp.timetracking.calendar.domain.event.WorkCalendarUpdated;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Reglas de dominio del agregado {@link WorkCalendar} (T70-01, RF-CAL-001..007,
 * diseno §9.3).
 */
class WorkCalendarTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-15T09:00:00Z");
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    private final Clock clock = () -> NOW;
    private final IdGenerator idGenerator = UUID::randomUUID;

    private static final List<CalendarDayRule> WEEKDAYS = List.of(
            CalendarDayRule.working(DayOfWeek.MONDAY, 480),
            CalendarDayRule.working(DayOfWeek.TUESDAY, 480),
            CalendarDayRule.working(DayOfWeek.WEDNESDAY, 480),
            CalendarDayRule.working(DayOfWeek.THURSDAY, 480),
            CalendarDayRule.working(DayOfWeek.FRIDAY, 420),
            CalendarDayRule.nonWorking(DayOfWeek.SATURDAY),
            CalendarDayRule.nonWorking(DayOfWeek.SUNDAY));

    private WorkCalendar calendar(List<Holiday> holidays, List<SpecialDay> specialDays) {
        return WorkCalendar.create(
                TENANT_ID,
                "General",
                "Europe/Madrid",
                FROM,
                TO,
                WEEKDAYS,
                holidays,
                specialDays,
                clock,
                idGenerator);
    }

    // --- Creacion -------------------------------------------------------

    @Test
    void createsActiveCalendarAndEmitsCreatedEvent() {
        WorkCalendar calendar = calendar(List.of(), List.of());

        assertThat(calendar.tenantId()).isEqualTo(TENANT_ID);
        assertThat(calendar.name()).isEqualTo("General");
        assertThat(calendar.status()).isEqualTo(CalendarStatus.ACTIVE);
        assertThat(calendar.zoneId()).isEqualTo(ZoneId.of("Europe/Madrid"));
        assertThat(calendar.createdAt()).isEqualTo(NOW);
        assertThat(calendar.updatedAt()).isEqualTo(NOW);
        assertThat(calendar.version()).isZero();
        assertThat(calendar.dayRules()).hasSize(7);

        List<Object> events = calendar.pullDomainEvents();
        assertThat(events).hasSize(1).first().isInstanceOf(WorkCalendarCreated.class);
        WorkCalendarCreated created = (WorkCalendarCreated) events.get(0);
        assertThat(created.tenantId()).isEqualTo(TENANT_ID);
        assertThat(created.validFrom()).isEqualTo(FROM);
        assertThat(created.validTo()).isEqualTo(TO);
        // Los eventos se entregan una sola vez.
        assertThat(calendar.pullDomainEvents()).isEmpty();
    }

    @Test
    void trimsNameAndRejectsBlankOrTooLongNames() {
        WorkCalendar trimmed = WorkCalendar.create(
                TENANT_ID, "  General  ", "UTC", FROM, null, List.of(), List.of(), List.of(), clock, idGenerator);
        assertThat(trimmed.name()).isEqualTo("General");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> WorkCalendar.create(
                        TENANT_ID, "  ", "UTC", FROM, null, List.of(), List.of(), List.of(), clock, idGenerator))
                .withMessageContaining("nombre");

        String tooLong = "x".repeat(WorkCalendar.MAX_NAME_LENGTH + 1);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WorkCalendar.create(
                        TENANT_ID, tooLong, "UTC", FROM, null, List.of(), List.of(), List.of(), clock, idGenerator));
    }

    @Test
    void rejectsInvalidTimezone() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WorkCalendar.create(
                        TENANT_ID,
                        "General",
                        "Marte/Olympus",
                        FROM,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        clock,
                        idGenerator))
                .withMessageContaining("IANA");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> WorkCalendar.create(
                        TENANT_ID, "General", " ", FROM, null, List.of(), List.of(), List.of(), clock, idGenerator))
                .withMessageContaining("zona horaria");
    }

    @Test
    void rejectsPeriodWithoutStartOrEndingBeforeStart() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WorkCalendar.create(
                        TENANT_ID, "General", "UTC", null, TO, List.of(), List.of(), List.of(), clock, idGenerator))
                .withMessageContaining("inicio de vigencia");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> WorkCalendar.create(
                        TENANT_ID,
                        "General",
                        "UTC",
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 5, 31),
                        List.of(),
                        List.of(),
                        List.of(),
                        clock,
                        idGenerator))
                .withMessageContaining("terminar antes");
    }

    @Test
    void rejectsDuplicateWeeklyRule() {
        assertThatExceptionOfType(DuplicateDayRuleException.class)
                .isThrownBy(() -> WorkCalendar.create(
                        TENANT_ID,
                        "General",
                        "UTC",
                        FROM,
                        null,
                        List.of(
                                CalendarDayRule.working(DayOfWeek.MONDAY, 480),
                                CalendarDayRule.nonWorking(DayOfWeek.MONDAY)),
                        List.of(),
                        List.of(),
                        clock,
                        idGenerator))
                .matches(ex -> ex.errorCode().equals("CALENDAR_DUPLICATE_DAY_RULE"));
    }

    @Test
    void rejectsSameDateTwiceAcrossHolidaysAndSpecialDays() {
        LocalDate date = LocalDate.of(2026, 1, 6);

        assertThatExceptionOfType(DuplicateCalendarDateException.class)
                .isThrownBy(() -> calendar(
                        List.of(new Holiday(date, "Reyes"), new Holiday(date, "Repetido")), List.of()));

        assertThatExceptionOfType(DuplicateCalendarDateException.class)
                .isThrownBy(() -> calendar(
                        List.of(new Holiday(date, "Reyes")), List.of(new SpecialDay(date, "Especial", 240))))
                .matches(ex -> ex.errorCode().equals("CALENDAR_DUPLICATE_DATE"));

        assertThatExceptionOfType(DuplicateCalendarDateException.class)
                .isThrownBy(() -> calendar(
                        List.of(),
                        List.of(new SpecialDay(date, "Especial", 240), new SpecialDay(date, "Otra", 120))));
    }

    @Test
    void acceptsNullCollectionsAsEmpty() {
        WorkCalendar calendar = WorkCalendar.create(
                TENANT_ID, "Vacio", "UTC", FROM, null, null, null, null, clock, idGenerator);

        assertThat(calendar.dayRules()).isEmpty();
        assertThat(calendar.holidays()).isEmpty();
        assertThat(calendar.specialDays()).isEmpty();
        // Sin reglas semanales ningun dia es laborable.
        assertThat(calendar.isWorkingDay(LocalDate.of(2026, 3, 2))).isFalse();
    }

    // --- Vigencia (RF-CAL-005) -----------------------------------------

    @Test
    void appliesValidityPeriodInclusivelyOnBothEnds() {
        WorkCalendar calendar = calendar(List.of(), List.of());

        assertThat(calendar.isEffectiveOn(FROM)).isTrue();
        assertThat(calendar.isEffectiveOn(TO)).isTrue();
        assertThat(calendar.isEffectiveOn(FROM.minusDays(1))).isFalse();
        assertThat(calendar.isEffectiveOn(TO.plusDays(1))).isFalse();
    }

    @Test
    void treatsNullValidToAsOpenEndedValidity() {
        WorkCalendar calendar = WorkCalendar.create(
                TENANT_ID, "Indefinido", "UTC", FROM, null, WEEKDAYS, List.of(), List.of(), clock, idGenerator);

        assertThat(calendar.isEffectiveOn(LocalDate.of(2099, 12, 31))).isTrue();
        assertThat(calendar.isEffectiveOn(FROM.minusDays(1))).isFalse();
    }

    @Test
    void reportsDayOutsideValidityAsNonWorkingWithExplicitSource() {
        WorkCalendar calendar = calendar(List.of(), List.of());
        // 2025-06-02 es lunes, laborable segun la regla semanal, pero cae fuera
        // de la vigencia: la vigencia manda sobre la regla.
        CalendarDay day = calendar.dayOf(LocalDate.of(2025, 6, 2));

        assertThat(day.source()).isEqualTo(DaySource.OUT_OF_VALIDITY);
        assertThat(day.working()).isFalse();
        assertThat(day.expectedMinutes()).isZero();
        assertThat(calendar.expectedHours(LocalDate.of(2025, 6, 2))).isEqualTo(Duration.ZERO);
    }

    @Test
    void isApplicableOnlyWhenActiveAndEffective() {
        WorkCalendar calendar = calendar(List.of(), List.of());
        LocalDate insideValidity = LocalDate.of(2026, 3, 2);

        assertThat(calendar.isApplicableOn(insideValidity)).isTrue();
        assertThat(calendar.isApplicableOn(LocalDate.of(2025, 3, 3))).isFalse();

        calendar.archive(clock, idGenerator);
        assertThat(calendar.isApplicableOn(insideValidity)).isFalse();
    }

    // --- Dias laborables y horas esperadas (RF-CAL-002) -----------------

    @Test
    void resolvesWorkingDayAndExpectedHoursFromWeeklyRule() {
        WorkCalendar calendar = calendar(List.of(), List.of());

        LocalDate monday = LocalDate.of(2026, 3, 2);
        assertThat(calendar.isWorkingDay(monday)).isTrue();
        assertThat(calendar.expectedHours(monday)).isEqualTo(Duration.ofHours(8));
        assertThat(calendar.dayOf(monday).source()).isEqualTo(DaySource.WEEKLY_RULE);

        LocalDate friday = LocalDate.of(2026, 3, 6);
        assertThat(calendar.expectedHours(friday)).isEqualTo(Duration.ofMinutes(420));

        LocalDate saturday = LocalDate.of(2026, 3, 7);
        assertThat(calendar.isWorkingDay(saturday)).isFalse();
        assertThat(calendar.expectedHours(saturday)).isEqualTo(Duration.ZERO);
        assertThat(calendar.dayOf(saturday).source()).isEqualTo(DaySource.WEEKLY_RULE);
    }

    @Test
    void treatsWeekdayWithoutExplicitRuleAsNonWorking() {
        WorkCalendar calendar = WorkCalendar.create(
                TENANT_ID,
                "Parcial",
                "UTC",
                FROM,
                null,
                List.of(CalendarDayRule.working(DayOfWeek.MONDAY, 480)),
                List.of(),
                List.of(),
                clock,
                idGenerator);

        assertThat(calendar.isWorkingDay(LocalDate.of(2026, 3, 2))).isTrue();
        // Martes: sin regla explicita.
        assertThat(calendar.isWorkingDay(LocalDate.of(2026, 3, 3))).isFalse();
    }

    // --- Festivos (RF-CAL-003) -----------------------------------------

    @Test
    void holidayOverridesWorkingWeeklyRule() {
        LocalDate epiphany = LocalDate.of(2026, 1, 6); // martes
        WorkCalendar calendar = calendar(List.of(new Holiday(epiphany, "Reyes")), List.of());

        CalendarDay day = calendar.dayOf(epiphany);
        assertThat(day.source()).isEqualTo(DaySource.HOLIDAY);
        assertThat(day.working()).isFalse();
        assertThat(calendar.expectedHours(epiphany)).isEqualTo(Duration.ZERO);
    }

    // --- Jornadas especiales (RF-CAL-004) ------------------------------

    @Test
    void specialDayOverridesWeeklyRuleWithReducedSchedule() {
        LocalDate christmasEve = LocalDate.of(2026, 12, 24); // jueves
        WorkCalendar calendar =
                calendar(List.of(), List.of(new SpecialDay(christmasEve, "Jornada intensiva", 300)));

        CalendarDay day = calendar.dayOf(christmasEve);
        assertThat(day.source()).isEqualTo(DaySource.SPECIAL_DAY);
        assertThat(day.working()).isTrue();
        assertThat(day.expectedMinutes()).isEqualTo(300);
    }

    @Test
    void specialDayCanTurnNonWorkingWeekdayIntoWorkingDay() {
        LocalDate saturday = LocalDate.of(2026, 3, 7);
        WorkCalendar calendar = calendar(List.of(), List.of(new SpecialDay(saturday, "Inventario", 240)));

        assertThat(calendar.isWorkingDay(saturday)).isTrue();
        assertThat(calendar.expectedHours(saturday)).isEqualTo(Duration.ofHours(4));
        assertThat(calendar.dayOf(saturday).source()).isEqualTo(DaySource.SPECIAL_DAY);
    }

    @Test
    void specialDayWithZeroMinutesTurnsWorkingDayIntoNonWorkingDay() {
        LocalDate bridge = LocalDate.of(2026, 3, 2); // lunes laborable
        WorkCalendar calendar = calendar(List.of(), List.of(new SpecialDay(bridge, "Puente de empresa", 0)));

        assertThat(calendar.isWorkingDay(bridge)).isFalse();
        assertThat(calendar.dayOf(bridge).source()).isEqualTo(DaySource.SPECIAL_DAY);
    }

    @Test
    void specialDayTakesPrecedenceOverHolidayOnDifferentDates() {
        // Precedencia global: jornada especial > festivo > regla semanal.
        LocalDate holiday = LocalDate.of(2026, 1, 6);
        LocalDate special = LocalDate.of(2026, 1, 7);
        WorkCalendar calendar =
                calendar(List.of(new Holiday(holiday, "Reyes")), List.of(new SpecialDay(special, "Especial", 240)));

        assertThat(calendar.dayOf(holiday).source()).isEqualTo(DaySource.HOLIDAY);
        assertThat(calendar.dayOf(special).source()).isEqualTo(DaySource.SPECIAL_DAY);
    }

    // --- Edicion y archivado (T70-04) ----------------------------------

    @Test
    void updateReplacesEveryCollectionAndEmitsUpdatedEvent() {
        WorkCalendar calendar = calendar(List.of(new Holiday(LocalDate.of(2026, 1, 6), "Reyes")), List.of());
        calendar.pullDomainEvents();

        calendar.update(
                "General 2027",
                "Atlantic/Canary",
                LocalDate.of(2027, 1, 1),
                null,
                List.of(CalendarDayRule.working(DayOfWeek.MONDAY, 300)),
                List.of(),
                List.of(new SpecialDay(LocalDate.of(2027, 8, 15), "Asuncion", 0)),
                clock,
                idGenerator);

        assertThat(calendar.name()).isEqualTo("General 2027");
        assertThat(calendar.timezone()).isEqualTo("Atlantic/Canary");
        assertThat(calendar.validTo()).isNull();
        assertThat(calendar.dayRules()).hasSize(1);
        assertThat(calendar.holidays()).isEmpty();
        assertThat(calendar.specialDays()).hasSize(1);

        List<Object> events = calendar.pullDomainEvents();
        assertThat(events).hasSize(1).first().isInstanceOf(WorkCalendarUpdated.class);
    }

    @Test
    void archiveMarksCalendarAndEmitsArchivedEvent() {
        WorkCalendar calendar = calendar(List.of(), List.of());
        calendar.pullDomainEvents();

        calendar.archive(clock, idGenerator);

        assertThat(calendar.status()).isEqualTo(CalendarStatus.ARCHIVED);
        List<Object> events = calendar.pullDomainEvents();
        assertThat(events).hasSize(1).first().isInstanceOf(WorkCalendarArchived.class);
        assertThat(((WorkCalendarArchived) events.get(0)).name()).isEqualTo("General");
    }

    @Test
    void rejectsUpdateAndSecondArchiveOnArchivedCalendar() {
        WorkCalendar calendar = calendar(List.of(), List.of());
        calendar.archive(clock, idGenerator);

        assertThatExceptionOfType(CalendarArchivedException.class)
                .isThrownBy(() -> calendar.archive(clock, idGenerator))
                .matches(ex -> ex.errorCode().equals("CALENDAR_ARCHIVED"));

        assertThatExceptionOfType(CalendarArchivedException.class)
                .isThrownBy(() -> calendar.update(
                        "Otro", "UTC", FROM, null, List.of(), List.of(), List.of(), clock, idGenerator));
    }

    // --- Reconstitucion -------------------------------------------------

    @Test
    void reconstituteRestoresStateWithoutEmittingEvents() {
        UUID id = UUID.randomUUID();
        WorkCalendar calendar = WorkCalendar.reconstitute(
                id,
                TENANT_ID,
                "Persistido",
                "Europe/Madrid",
                FROM,
                TO,
                CalendarStatus.ARCHIVED,
                List.of(CalendarDayRule.working(DayOfWeek.MONDAY, 480)),
                List.of(new Holiday(LocalDate.of(2026, 1, 6), "Reyes")),
                List.of(new SpecialDay(LocalDate.of(2026, 12, 24), "Intensiva", 300)),
                7L,
                NOW,
                NOW);

        assertThat(calendar.id()).isEqualTo(id);
        assertThat(calendar.status()).isEqualTo(CalendarStatus.ARCHIVED);
        assertThat(calendar.version()).isEqualTo(7L);
        assertThat(calendar.pullDomainEvents()).isEmpty();
    }

    @Test
    void rejectsNullArgumentsOnFactories() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> WorkCalendar.create(
                        null, "General", "UTC", FROM, null, List.of(), List.of(), List.of(), clock, idGenerator));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> WorkCalendar.create(
                        TENANT_ID, "General", "UTC", FROM, null, List.of(), List.of(), List.of(), null, idGenerator));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> WorkCalendar.reconstitute(
                        null,
                        TENANT_ID,
                        "General",
                        "UTC",
                        FROM,
                        null,
                        CalendarStatus.ACTIVE,
                        List.of(),
                        List.of(),
                        List.of(),
                        0L,
                        NOW,
                        NOW));
        WorkCalendar calendar = calendar(List.of(), List.of());
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> calendar.dayOf(null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> calendar.isEffectiveOn(null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> calendar.startOfDay(null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> calendar.endOfDayExclusive(null));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> calendar.archive(null, idGenerator));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> calendar.update(
                        "x", "UTC", FROM, null, List.of(), List.of(), List.of(), null, idGenerator));
    }

    @Test
    void returnsDefensiveCopiesOfCollections() {
        WorkCalendar calendar = calendar(List.of(new Holiday(LocalDate.of(2026, 1, 6), "Reyes")), List.of());

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> calendar.holidays().add(new Holiday(LocalDate.of(2026, 5, 1), "Trabajo")));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> calendar.dayRules().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> calendar.specialDays().clear());
    }
}
