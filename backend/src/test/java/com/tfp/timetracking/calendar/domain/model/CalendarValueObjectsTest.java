package com.tfp.timetracking.calendar.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Invariantes de los objetos de valor del calendario: {@link CalendarDayRule},
 * {@link Holiday}, {@link SpecialDay}, {@link CalendarDay} y la precedencia
 * declarada en {@link AssignmentScope}.
 */
class CalendarValueObjectsTest {

    private static final LocalDate DATE = LocalDate.of(2026, 1, 6);

    // --- CalendarDayRule ------------------------------------------------

    @Test
    void workingRuleRequiresPositiveMinutesWithinADay() {
        CalendarDayRule rule = CalendarDayRule.working(DayOfWeek.MONDAY, 480);
        assertThat(rule.working()).isTrue();
        assertThat(rule.expectedMinutes()).isEqualTo(480);
        assertThat(rule.expectedDuration()).isEqualTo(Duration.ofHours(8));

        assertThatIllegalArgumentException().isThrownBy(() -> CalendarDayRule.working(DayOfWeek.MONDAY, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> CalendarDayRule.working(DayOfWeek.MONDAY, -1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CalendarDayRule.working(DayOfWeek.MONDAY, CalendarDayRule.MAX_EXPECTED_MINUTES + 1));
        assertThat(CalendarDayRule.working(DayOfWeek.MONDAY, CalendarDayRule.MAX_EXPECTED_MINUTES).expectedMinutes())
                .isEqualTo(1440);
    }

    @Test
    void nonWorkingRuleHasZeroExpectedMinutes() {
        CalendarDayRule rule = CalendarDayRule.nonWorking(DayOfWeek.SUNDAY);
        assertThat(rule.working()).isFalse();
        assertThat(rule.expectedMinutes()).isZero();
        assertThat(rule.expectedDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    void generalFactoryRejectsIncoherentCombination() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CalendarDayRule.of(DayOfWeek.SUNDAY, false, 120))
                .withMessageContaining("no laborable");
        assertThat(CalendarDayRule.of(DayOfWeek.SUNDAY, false, 0)).isEqualTo(CalendarDayRule.nonWorking(DayOfWeek.SUNDAY));
        assertThat(CalendarDayRule.of(DayOfWeek.MONDAY, true, 480))
                .isEqualTo(CalendarDayRule.working(DayOfWeek.MONDAY, 480));
    }

    @Test
    void dayRuleEqualityIsByValue() {
        CalendarDayRule monday = CalendarDayRule.working(DayOfWeek.MONDAY, 480);

        assertThat(monday)
                .isEqualTo(CalendarDayRule.working(DayOfWeek.MONDAY, 480))
                .isEqualTo(monday)
                .hasSameHashCodeAs(CalendarDayRule.working(DayOfWeek.MONDAY, 480))
                .isNotEqualTo(CalendarDayRule.working(DayOfWeek.MONDAY, 300))
                .isNotEqualTo(CalendarDayRule.working(DayOfWeek.TUESDAY, 480))
                .isNotEqualTo(CalendarDayRule.nonWorking(DayOfWeek.MONDAY))
                .isNotEqualTo("MONDAY")
                .isNotEqualTo(null);
        assertThat(monday.toString()).contains("MONDAY").contains("480");
    }

    @Test
    void dayRuleRejectsNullDayOfWeek() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> CalendarDayRule.working(null, 480));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> CalendarDayRule.nonWorking(null));
    }

    // --- Holiday --------------------------------------------------------

    @Test
    void holidayTrimsNameAndRequiresDateAndName() {
        assertThat(new Holiday(DATE, "  Reyes  ").name()).isEqualTo("Reyes");

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new Holiday(null, "Reyes"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Holiday(DATE, "  "));
        assertThatIllegalArgumentException().isThrownBy(() -> new Holiday(DATE, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Holiday(DATE, "x".repeat(Holiday.MAX_NAME_LENGTH + 1)));
    }

    // --- SpecialDay -----------------------------------------------------

    @Test
    void specialDayIsWorkingOnlyWithPositiveMinutes() {
        assertThat(new SpecialDay(DATE, "Intensiva", 300).working()).isTrue();
        assertThat(new SpecialDay(DATE, "Intensiva", 300).expectedDuration()).isEqualTo(Duration.ofHours(5));
        assertThat(new SpecialDay(DATE, "Puente", 0).working()).isFalse();
    }

    @Test
    void specialDayValidatesNameAndMinutes() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new SpecialDay(null, "x", 60));
        assertThatIllegalArgumentException().isThrownBy(() -> new SpecialDay(DATE, " ", 60));
        assertThatIllegalArgumentException().isThrownBy(() -> new SpecialDay(DATE, null, 60));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SpecialDay(DATE, "x".repeat(SpecialDay.MAX_NAME_LENGTH + 1), 60));
        assertThatIllegalArgumentException().isThrownBy(() -> new SpecialDay(DATE, "x", -1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SpecialDay(DATE, "x", CalendarDayRule.MAX_EXPECTED_MINUTES + 1));
    }

    // --- CalendarDay ----------------------------------------------------

    @Test
    void calendarDayRejectsIncoherentState() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CalendarDay(DATE, false, 120, DaySource.WEEKLY_RULE));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CalendarDay(DATE, true, -5, DaySource.WEEKLY_RULE));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new CalendarDay(null, false, 0, DaySource.HOLIDAY));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new CalendarDay(DATE, false, 0, null));

        assertThat(new CalendarDay(DATE, true, 480, DaySource.WEEKLY_RULE).expectedDuration())
                .isEqualTo(Duration.ofHours(8));
    }

    // --- AssignmentScope: contrato de precedencia -----------------------

    @Test
    void scopeSpecificityOrdersEmployeeOverTeamOverTenant() {
        assertThat(AssignmentScope.EMPLOYEE.specificity())
                .isGreaterThan(AssignmentScope.TEAM.specificity());
        assertThat(AssignmentScope.TEAM.specificity()).isGreaterThan(AssignmentScope.TENANT.specificity());
    }

    @Test
    void onlyTenantScopeOmitsTarget() {
        assertThat(AssignmentScope.TENANT.requiresTarget()).isFalse();
        assertThat(AssignmentScope.TEAM.requiresTarget()).isTrue();
        assertThat(AssignmentScope.EMPLOYEE.requiresTarget()).isTrue();
    }
}
