package com.tfp.timetracking.timetracking.interfaces.rest;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.timetracking.domain.BreakEntry;
import com.tfp.timetracking.timetracking.domain.Workday;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkdayRestMapper {

    private final Clock clock;

    public WorkdayRestMapper(Clock clock) {
        this.clock = clock;
    }

    public WorkdayResponse toResponse(Workday workday) {
        return new WorkdayResponse(
                workday.id(),
                workday.status().name(),
                workday.startedAt(),
                workday.endedAt(),
                workday.breaks().stream().map(this::toBreakResponse).toList(),
                workedDuration(workday));
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
}
