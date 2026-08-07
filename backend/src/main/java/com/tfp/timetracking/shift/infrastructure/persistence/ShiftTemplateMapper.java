package com.tfp.timetracking.shift.infrastructure.persistence;

import com.tfp.timetracking.shift.domain.model.ShiftBreakPolicy;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateStatus;
import java.time.Duration;

final class ShiftTemplateMapper {

    private ShiftTemplateMapper() {}

    static ShiftTemplateJpaEntity toJpaEntity(ShiftTemplate template) {
        return new ShiftTemplateJpaEntity(
                template.id(),
                template.tenantId(),
                template.name(),
                template.startTime(),
                template.endTime(),
                Math.toIntExact(template.breakPolicy().plannedBreakDuration().toMinutes()),
                template.status().name());
    }

    static ShiftTemplate toDomain(ShiftTemplateJpaEntity entity) {
        return ShiftTemplate.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getStartTime(),
                entity.getEndTime(),
                new ShiftBreakPolicy(Duration.ofMinutes(entity.getPlannedBreakMinutes())),
                ShiftTemplateStatus.valueOf(entity.getStatus()));
    }
}
