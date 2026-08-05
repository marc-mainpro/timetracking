package com.tfp.timetracking.timetracking.interfaces.rest;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.timetracking.domain.BreakEntry;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluation;
import com.tfp.timetracking.timetracking.domain.WorkdayEvaluationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkdayRestMapper {

    private final Clock clock;
    private final WorkdayEvaluationRepository workdayEvaluationRepository;
    private final TenantContext tenantContext;

    public WorkdayRestMapper(
            Clock clock, WorkdayEvaluationRepository workdayEvaluationRepository, TenantContext tenantContext) {
        this.clock = clock;
        this.workdayEvaluationRepository = workdayEvaluationRepository;
        this.tenantContext = tenantContext;
    }

    public WorkdayResponse toResponse(Workday workday) {
        return new WorkdayResponse(
                workday.id(),
                workday.status().name(),
                workday.startedAt(),
                workday.endedAt(),
                workday.breaks().stream().map(this::toBreakResponse).toList(),
                workedDuration(workday),
                loadEvaluation(workday));
    }

    public PagedResponse<WorkdayResponse> toPagedResponse(PagedResult<Workday> result) {
        return new PagedResponse<>(
                result.content().stream().map(this::toResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    private BreakEntryResponse toBreakResponse(BreakEntry breakEntry) {
        return new BreakEntryResponse(breakEntry.id(), breakEntry.startedAt(), breakEntry.endedAt());
    }

    private Duration workedDuration(Workday workday) {
        Instant end = workday.endedAt() != null ? workday.endedAt() : clock.now();
        Duration total = Duration.between(workday.startedAt(), end);
        Duration breaks = workday.breaks().stream()
                .filter(breakEntry -> breakEntry.endedAt() != null)
                .map(breakEntry -> Duration.between(breakEntry.startedAt(), breakEntry.endedAt()))
                .reduce(Duration.ZERO, Duration::plus);
        return total.minus(breaks);
    }

    private WorkdayEvaluationResponse loadEvaluation(Workday workday) {
        return workdayEvaluationRepository.findByWorkdayId(tenantContext.currentTenantId(), workday.id())
                .map(this::toEvaluationResponse)
                .orElse(null);
    }

    private WorkdayEvaluationResponse toEvaluationResponse(WorkdayEvaluation evaluation) {
        return new WorkdayEvaluationResponse(
                evaluation.expectedDuration(),
                evaluation.workedDuration(),
                evaluation.pausedDuration(),
                evaluation.overtimeDuration(),
                evaluation.anomalies().stream().map(Enum::name).toList());
    }
}
