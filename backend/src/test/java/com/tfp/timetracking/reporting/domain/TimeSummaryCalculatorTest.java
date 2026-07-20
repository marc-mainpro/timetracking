package com.tfp.timetracking.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TimeSummaryCalculatorTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final UUID EMPLOYEE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void computesWorkedMinusPausesForASimpleClosedWorkday() {
        WorkdayReportEntry entry = closed(
                Instant.parse("2026-03-10T08:00:00Z"),
                Instant.parse("2026-03-10T17:00:00Z"),
                List.of(new BreakInterval(Instant.parse("2026-03-10T12:00:00Z"), Instant.parse("2026-03-10T12:30:00Z"))));

        List<EmployeeDaySummary> result = TimeSummaryCalculator.summarizeByDay(List.of(entry), MADRID);

        assertThat(result).hasSize(1);
        EmployeeDaySummary day = result.get(0);
        assertThat(day.worked()).isEqualTo(Duration.ofHours(8).plusMinutes(30));
        assertThat(day.paused()).isEqualTo(Duration.ofMinutes(30));
        assertThat(day.workdayCount()).isEqualTo(1);
        assertThat(day.adjustedWorkdayCount()).isZero();
        assertThat(day.openWorkdays()).isZero();
    }

    @Test
    void splitsAWorkdayThatCrossesLocalMidnight() {
        // 2026-03-10 23:30 Madrid (UTC+1) -> 2026-03-11 01:30 Madrid (UTC+1)
        WorkdayReportEntry entry = closed(Instant.parse("2026-03-10T22:30:00Z"), Instant.parse("2026-03-11T00:30:00Z"), List.of());

        List<EmployeeDaySummary> result = TimeSummaryCalculator.summarizeByDay(List.of(entry), MADRID);

        assertThat(result).hasSize(2);
        EmployeeDaySummary firstDay = result.get(0);
        EmployeeDaySummary secondDay = result.get(1);
        assertThat(firstDay.day()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(firstDay.worked()).isEqualTo(Duration.ofMinutes(30));
        assertThat(firstDay.workdayCount()).isEqualTo(1);
        assertThat(secondDay.day()).isEqualTo(LocalDate.of(2026, 3, 11));
        assertThat(secondDay.worked()).isEqualTo(Duration.ofMinutes(90));
        assertThat(secondDay.workdayCount()).isZero();
    }

    @Test
    void handlesTheTwentyThreeHourDstSpringForwardDayInMadrid() {
        // Europe/Madrid, 2026-03-29: cambia de UTC+1 a UTC+2 a las 02:00 local -> el dia dura 23h.
        LocalDate dstDay = LocalDate.of(2026, 3, 29);
        Instant dayStart = dstDay.atStartOfDay(MADRID).toInstant();
        Instant dayEnd = dstDay.plusDays(1).atStartOfDay(MADRID).toInstant();

        assertThat(Duration.between(dayStart, dayEnd)).isEqualTo(Duration.ofHours(23));

        WorkdayReportEntry entry = closed(dayStart, dayEnd, List.of());

        List<EmployeeDaySummary> result = TimeSummaryCalculator.summarizeByDay(List.of(entry), MADRID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).day()).isEqualTo(dstDay);
        assertThat(result.get(0).worked()).isEqualTo(Duration.ofHours(23));
    }

    @Test
    void handlesTheTwentyFiveHourDstFallBackDayInMadrid() {
        // Europe/Madrid, 2026-10-25: cambia de UTC+2 a UTC+1 a las 03:00 local -> el dia dura 25h.
        LocalDate dstDay = LocalDate.of(2026, 10, 25);
        Instant dayStart = dstDay.atStartOfDay(MADRID).toInstant();
        Instant dayEnd = dstDay.plusDays(1).atStartOfDay(MADRID).toInstant();

        assertThat(Duration.between(dayStart, dayEnd)).isEqualTo(Duration.ofHours(25));

        WorkdayReportEntry entry = closed(dayStart, dayEnd, List.of());

        List<EmployeeDaySummary> result = TimeSummaryCalculator.summarizeByDay(List.of(entry), MADRID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).day()).isEqualTo(dstDay);
        assertThat(result.get(0).worked()).isEqualTo(Duration.ofHours(25));
    }

    @Test
    void openWorkdaysAreExcludedFromTotalsButCountedSeparately() {
        WorkdayReportEntry openEntry =
                new WorkdayReportEntry(UUID.randomUUID(), EMPLOYEE, true, false, Instant.parse("2026-03-10T08:00:00Z"), null, List.of());
        WorkdayReportEntry closedEntry = closed(Instant.parse("2026-03-10T09:00:00Z"), Instant.parse("2026-03-10T10:00:00Z"), List.of());

        List<EmployeeDaySummary> result = TimeSummaryCalculator.summarizeByDay(List.of(openEntry, closedEntry), MADRID);

        assertThat(result).hasSize(1);
        EmployeeDaySummary day = result.get(0);
        assertThat(day.worked()).isEqualTo(Duration.ofHours(1));
        assertThat(day.openWorkdays()).isEqualTo(1);
        assertThat(day.workdayCount()).isEqualTo(1);
    }

    @Test
    void adjustedWorkdaysAreCountedSeparately() {
        WorkdayReportEntry entry = new WorkdayReportEntry(
                UUID.randomUUID(),
                EMPLOYEE,
                false,
                true,
                Instant.parse("2026-03-10T08:00:00Z"),
                Instant.parse("2026-03-10T10:00:00Z"),
                List.of());

        List<EmployeeDaySummary> result = TimeSummaryCalculator.summarizeByDay(List.of(entry), MADRID);

        assertThat(result.get(0).adjustedWorkdayCount()).isEqualTo(1);
        assertThat(result.get(0).workdayCount()).isEqualTo(1);
    }

    @Test
    void summarizeTotalsByEmployeeAggregatesAcrossTheWholeRangeIrrespectiveOfDayBoundaries() {
        WorkdayReportEntry crossingMidnight =
                closed(Instant.parse("2026-03-10T22:30:00Z"), Instant.parse("2026-03-11T00:30:00Z"), List.of());
        WorkdayReportEntry openEntry =
                new WorkdayReportEntry(UUID.randomUUID(), EMPLOYEE, true, false, Instant.parse("2026-03-12T08:00:00Z"), null, List.of());

        List<TenantEmployeeSummary> result =
                TimeSummaryCalculator.summarizeTotalsByEmployee(List.of(crossingMidnight, openEntry));

        assertThat(result).hasSize(1);
        TenantEmployeeSummary summary = result.get(0);
        assertThat(summary.employeeId()).isEqualTo(EMPLOYEE);
        assertThat(summary.worked()).isEqualTo(Duration.ofHours(2));
        assertThat(summary.workdayCount()).isEqualTo(1);
        assertThat(summary.openWorkdays()).isEqualTo(1);
    }

    private WorkdayReportEntry closed(Instant startedAt, Instant endedAt, List<BreakInterval> breaks) {
        return new WorkdayReportEntry(UUID.randomUUID(), EMPLOYEE, false, false, startedAt, endedAt, breaks);
    }
}
