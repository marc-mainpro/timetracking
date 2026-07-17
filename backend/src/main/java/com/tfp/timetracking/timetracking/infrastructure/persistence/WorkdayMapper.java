package com.tfp.timetracking.timetracking.infrastructure.persistence;

import com.tfp.timetracking.timetracking.domain.BreakEntry;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayStatus;
import java.util.ArrayList;
import java.util.List;

final class WorkdayMapper {

    private WorkdayMapper() {}

    static WorkdayJpaEntity toJpaEntity(Workday workday) {
        WorkdayJpaEntity entity = new WorkdayJpaEntity(
                workday.id(),
                workday.tenantId(),
                workday.employeeId(),
                workday.status().name(),
                workday.startedAt(),
                workday.endedAt(),
                workday.version(),
                workday.createdAt(),
                workday.updatedAt());
        List<BreakEntryJpaEntity> breakEntries = new ArrayList<>();
        for (BreakEntry breakEntry : workday.breaks()) {
            breakEntries.add(new BreakEntryJpaEntity(
                    breakEntry.id(), entity, breakEntry.startedAt(), breakEntry.endedAt()));
        }
        entity.getBreaks().addAll(breakEntries);
        return entity;
    }

    static Workday toDomain(WorkdayJpaEntity entity) {
        List<BreakEntry> breaks = entity.getBreaks().stream()
                .map(breakEntity -> BreakEntry.reconstitute(
                        breakEntity.getId(), breakEntity.getStartedAt(), breakEntity.getEndedAt()))
                .toList();
        return Workday.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmployeeId(),
                WorkdayStatus.valueOf(entity.getStatus()),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                breaks);
    }
}
