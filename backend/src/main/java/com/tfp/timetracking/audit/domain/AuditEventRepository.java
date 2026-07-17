package com.tfp.timetracking.audit.domain;

import com.tfp.timetracking.shared.domain.PagedResult;
import java.time.Instant;
import java.util.UUID;

public interface AuditEventRepository {

    AuditEvent save(AuditEvent auditEvent);

    PagedResult<AuditEvent> findByTenant(UUID tenantId, String action, Instant from, Instant to, int page, int size);
}
