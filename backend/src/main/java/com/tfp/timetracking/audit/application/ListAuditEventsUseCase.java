package com.tfp.timetracking.audit.application;

import com.tfp.timetracking.audit.domain.AuditEvent;
import com.tfp.timetracking.audit.domain.AuditEventRepository;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ListAuditEventsUseCase {

    private final AuditEventRepository auditEventRepository;
    private final TenantContext tenantContext;

    public ListAuditEventsUseCase(AuditEventRepository auditEventRepository, TenantContext tenantContext) {
        this.auditEventRepository = auditEventRepository;
        this.tenantContext = tenantContext;
    }

    public PagedResult<AuditEvent> list(int page, int size, String action, Instant from, Instant to) {
        return auditEventRepository.findByTenant(tenantContext.currentTenantId(), action, from, to, page, size);
    }
}
