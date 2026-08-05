package com.tfp.timetracking.timetracking.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;
import com.tfp.timetracking.calendar.domain.model.CalendarDay;
import com.tfp.timetracking.calendar.domain.model.DaySource;
import com.tfp.timetracking.calendar.domain.model.EffectiveCalendar;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.timetracking.domain.HourlyRules;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayAnomaly;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluation;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkdayEvaluationEngineTest {

    private final WorkdayEvaluationEngine engine = new WorkdayEvaluationEngine();
    private final IdGenerator idGenerator = UUID::randomUUID;

    @Test
    void calculatesExpectedWorkedPausedAndOvertime() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Workday workday = Workday.start(tenantId, employeeId, Instant.parse("2026-01-15T09:00:00Z"), idGenerator);
        workday.pullDomainEvents();
        workday.startBreak(Instant.parse("2026-01-15T12:00:00Z"), idGenerator);
        workday.endBreak(Instant.parse("2026-01-15T12:30:00Z"), idGenerator);
        workday.close(Instant.parse("2026-01-15T18:00:00Z"), idGenerator);

        WorkdayEvaluation evaluation = engine.evaluate(
                workday,
                effectiveCalendar(tenantId, employeeId, Duration.ofHours(8)),
                new HourlyRules(tenantId, Duration.ofHours(9), Duration.ofMinutes(30), null, null));

        assertThat(evaluation.expectedDuration()).isEqualTo(Duration.ofHours(8));
        assertThat(evaluation.workedDuration()).isEqualTo(Duration.ofHours(8).plusMinutes(30));
        assertThat(evaluation.effectiveWorkedDuration()).isEqualTo(Duration.ofHours(8).plusMinutes(30));
        assertThat(evaluation.pausedDuration()).isEqualTo(Duration.ofMinutes(30));
        assertThat(evaluation.overtimeDuration()).isEqualTo(Duration.ofMinutes(30));
        assertThat(evaluation.deviationDuration()).isEqualTo(Duration.ZERO);
        assertThat(evaluation.anomalies()).isEmpty();
    }

    @Test
    void flagsMaximumDailyWorkAndMissingBreak() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Workday workday = Workday.reconstitute(
                UUID.randomUUID(),
                tenantId,
                employeeId,
                com.tfp.timetracking.timetracking.domain.WorkdayStatus.CLOSED,
                Instant.parse("2026-01-15T09:00:00Z"),
                Instant.parse("2026-01-15T20:00:00Z"),
                0L,
                Instant.parse("2026-01-15T09:00:00Z"),
                Instant.parse("2026-01-15T20:00:00Z"),
                List.of());

        WorkdayEvaluation evaluation = engine.evaluate(
                workday,
                effectiveCalendar(tenantId, employeeId, Duration.ofHours(8)),
                new HourlyRules(tenantId, Duration.ofHours(8), Duration.ofMinutes(30), null, null));

        assertThat(evaluation.anomalies())
                .containsExactlyInAnyOrder(WorkdayAnomaly.MAX_DAILY_WORK_EXCEEDED, WorkdayAnomaly.REQUIRED_BREAK_NOT_MET);
    }

    @Test
    void roundsWorkedDurationAndAppliesTolerance() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Workday workday = Workday.reconstitute(
                UUID.randomUUID(),
                tenantId,
                employeeId,
                com.tfp.timetracking.timetracking.domain.WorkdayStatus.CLOSED,
                Instant.parse("2026-01-15T09:00:00Z"),
                Instant.parse("2026-01-15T17:08:00Z"),
                0L,
                Instant.parse("2026-01-15T09:00:00Z"),
                Instant.parse("2026-01-15T17:08:00Z"),
                List.of());

        WorkdayEvaluation evaluation = engine.evaluate(
                workday,
                effectiveCalendar(tenantId, employeeId, Duration.ofHours(8)),
                new HourlyRules(tenantId, Duration.ofHours(8), Duration.ZERO, Duration.ofMinutes(15), Duration.ofMinutes(10)));

        assertThat(evaluation.workedDuration()).isEqualTo(Duration.ofHours(8).plusMinutes(8));
        assertThat(evaluation.effectiveWorkedDuration()).isEqualTo(Duration.ofHours(8).plusMinutes(15));
        assertThat(evaluation.overtimeDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(evaluation.deviationDuration()).isEqualTo(Duration.ZERO);
    }

    private EffectiveCalendar effectiveCalendar(UUID tenantId, UUID employeeId, Duration expectedHours) {
        CalendarAssignment assignment = CalendarAssignment.reconstitute(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), AssignmentScope.EMPLOYEE, employeeId, Instant.now(), Instant.now());
        WorkCalendar calendar = WorkCalendar.reconstitute(
                UUID.randomUUID(),
                tenantId,
                "General",
                "Europe/Madrid",
                LocalDate.parse("2026-01-01"),
                null,
                com.tfp.timetracking.calendar.domain.model.CalendarStatus.ACTIVE,
                List.of(),
                List.of(),
                List.of(),
                0L,
                Instant.now(),
                Instant.now());
        return new EffectiveCalendar(
                assignment,
                calendar,
                new CalendarDay(LocalDate.parse("2026-01-15"), true, Math.toIntExact(expectedHours.toMinutes()), DaySource.WEEKLY_RULE));
    }
}
