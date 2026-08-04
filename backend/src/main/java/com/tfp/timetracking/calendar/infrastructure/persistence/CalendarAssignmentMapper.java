package com.tfp.timetracking.calendar.infrastructure.persistence;

import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;

/** Mapper dominio &lt;-&gt; JPA para {@link CalendarAssignment}. */
final class CalendarAssignmentMapper {

    private CalendarAssignmentMapper() {}

    static CalendarAssignmentJpaEntity toJpaEntity(CalendarAssignment assignment) {
        return new CalendarAssignmentJpaEntity(
                assignment.id(),
                assignment.tenantId(),
                assignment.calendarId(),
                assignment.scope().name(),
                assignment.targetId(),
                assignment.createdAt(),
                assignment.updatedAt());
    }

    static CalendarAssignment toDomain(CalendarAssignmentJpaEntity entity) {
        return CalendarAssignment.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getCalendarId(),
                AssignmentScope.valueOf(entity.getScope()),
                entity.getScopeTargetId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
