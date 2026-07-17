package com.tfp.timetracking.audit.interfaces.rest;

import com.tfp.timetracking.audit.domain.AuditEvent;
import com.tfp.timetracking.shared.domain.PagedResult;
import org.springframework.stereotype.Component;

@Component
public class AuditEventRestMapper {

    public AuditEventResponse toResponse(AuditEvent auditEvent) {
        return new AuditEventResponse(
                auditEvent.id(),
                auditEvent.tenantId(),
                auditEvent.actorUserId(),
                auditEvent.action(),
                auditEvent.entityType(),
                auditEvent.entityId(),
                auditEvent.correlationId(),
                auditEvent.metadata(),
                auditEvent.occurredAt());
    }

    public PagedAuditEventsResponse toPagedResponse(PagedResult<AuditEvent> result) {
        return new PagedAuditEventsResponse(
                result.content().stream().map(this::toResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
