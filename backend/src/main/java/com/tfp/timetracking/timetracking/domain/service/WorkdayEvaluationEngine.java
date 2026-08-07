package com.tfp.timetracking.timetracking.domain.service;

import com.tfp.timetracking.calendar.domain.model.EffectiveCalendar;
import com.tfp.timetracking.timetracking.domain.HourlyRules;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayAnomaly;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class WorkdayEvaluationEngine {

    public WorkdayEvaluation evaluate(Workday workday, EffectiveCalendar effectiveCalendar, HourlyRules hourlyRules) {
        return evaluate(workday, effectiveCalendar, hourlyRules, null);
    }

    /**
     * Evalua la jornada tomando como previsto el turno asignado cuando lo hay
     * (T90-06, RF-SHF-005).
     *
     * <p>El turno <b>prevalece sobre el calendario</b>: es la planificacion mas
     * especifica que existe para ese empleado y ese dia, mientras que el
     * calendario describe la jornada tipica de su ambito. Si no hay turno, se
     * cae al calendario, y si tampoco lo hay el previsto es cero.
     *
     * @param plannedShiftWorkDuration tiempo de trabajo previsto por el turno,
     *     ya neto de la pausa prevista; {@code null} si no hay turno asignado
     */
    public WorkdayEvaluation evaluate(
            Workday workday,
            EffectiveCalendar effectiveCalendar,
            HourlyRules hourlyRules,
            Duration plannedShiftWorkDuration) {
        Duration workedDuration = workedDuration(workday);
        Duration pausedDuration = pausedDuration(workday);
        Duration expectedDuration = expectedDuration(effectiveCalendar, plannedShiftWorkDuration);
        Duration effectiveWorkedDuration = applyRounding(workedDuration, hourlyRules);
        Duration tolerance = hourlyRules != null && hourlyRules.tolerance() != null ? hourlyRules.tolerance() : Duration.ZERO;
        Duration overtimeDuration = positiveDifference(effectiveWorkedDuration, expectedDuration.plus(tolerance));
        Duration deviationDuration = positiveDifference(expectedDuration.minus(tolerance), effectiveWorkedDuration);

        List<WorkdayAnomaly> anomalies = new ArrayList<>();
        if (hourlyRules != null
                && hourlyRules.maxDailyWork() != null
                && effectiveWorkedDuration.compareTo(hourlyRules.maxDailyWork().plus(tolerance)) > 0) {
            anomalies.add(WorkdayAnomaly.MAX_DAILY_WORK_EXCEEDED);
        }
        if (hourlyRules != null
                && hourlyRules.requiredBreak() != null
                && pausedDuration.compareTo(nonNegative(hourlyRules.requiredBreak().minus(tolerance))) < 0) {
            anomalies.add(WorkdayAnomaly.REQUIRED_BREAK_NOT_MET);
        }

        return WorkdayEvaluation.reconstitute(
                workday.id(),
                workday.tenantId(),
                workday.employeeId(),
                expectedDuration,
                workedDuration,
                effectiveWorkedDuration,
                pausedDuration,
                overtimeDuration,
                deviationDuration,
                anomalies,
                java.time.Instant.EPOCH);
    }

    private Duration expectedDuration(EffectiveCalendar effectiveCalendar, Duration plannedShiftWorkDuration) {
        if (plannedShiftWorkDuration != null) {
            return plannedShiftWorkDuration;
        }
        return effectiveCalendar != null ? effectiveCalendar.expectedHours() : Duration.ZERO;
    }

    private Duration workedDuration(Workday workday) {
        if (workday.endedAt() == null) {
            return Duration.ZERO;
        }
        return Duration.between(workday.startedAt(), workday.endedAt()).minus(pausedDuration(workday));
    }

    private Duration pausedDuration(Workday workday) {
        return workday.breaks().stream()
                .filter(breakEntry -> breakEntry.endedAt() != null)
                .map(breakEntry -> Duration.between(breakEntry.startedAt(), breakEntry.endedAt()))
                .reduce(Duration.ZERO, Duration::plus);
    }

    private Duration positiveDifference(Duration left, Duration right) {
        Duration diff = left.minus(right);
        return diff.isNegative() ? Duration.ZERO : diff;
    }

    private Duration applyRounding(Duration workedDuration, HourlyRules hourlyRules) {
        if (hourlyRules == null || hourlyRules.roundingStep() == null) {
            return workedDuration;
        }
        long stepMinutes = hourlyRules.roundingStep().toMinutes();
        long workedMinutes = workedDuration.toMinutes();
        long roundedMinutes = Math.round((double) workedMinutes / stepMinutes) * stepMinutes;
        return Duration.ofMinutes(roundedMinutes);
    }

    private Duration nonNegative(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
