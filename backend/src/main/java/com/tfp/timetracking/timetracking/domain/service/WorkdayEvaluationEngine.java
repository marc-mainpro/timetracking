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
        Duration workedDuration = workedDuration(workday);
        Duration pausedDuration = pausedDuration(workday);
        Duration expectedDuration = effectiveCalendar != null ? effectiveCalendar.expectedHours() : Duration.ZERO;
        Duration overtimeDuration = effectiveCalendar == null ? Duration.ZERO : positiveDifference(workedDuration, expectedDuration);

        List<WorkdayAnomaly> anomalies = new ArrayList<>();
        if (hourlyRules != null && hourlyRules.maxDailyWork() != null && workedDuration.compareTo(hourlyRules.maxDailyWork()) > 0) {
            anomalies.add(WorkdayAnomaly.MAX_DAILY_WORK_EXCEEDED);
        }
        if (hourlyRules != null && hourlyRules.requiredBreak() != null && pausedDuration.compareTo(hourlyRules.requiredBreak()) < 0) {
            anomalies.add(WorkdayAnomaly.REQUIRED_BREAK_NOT_MET);
        }

        return WorkdayEvaluation.reconstitute(
                workday.id(),
                workday.tenantId(),
                workday.employeeId(),
                expectedDuration,
                workedDuration,
                pausedDuration,
                overtimeDuration,
                anomalies,
                java.time.Instant.EPOCH);
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
}
