package com.tfp.timetracking.audit.infrastructure;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.audit.domain.AuditEvent;
import com.tfp.timetracking.audit.domain.AuditEventRepository;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.infrastructure.security.CorrelationIdFilter;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class JpaAuditRecorder implements AuditRecorder {

    private final AuditEventRepository auditEventRepository;
    private final TenantContext tenantContext;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public JpaAuditRecorder(
            AuditEventRepository auditEventRepository, TenantContext tenantContext, Clock clock, IdGenerator idGenerator) {
        this.auditEventRepository = auditEventRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public void record(String action, String entityType, UUID entityId, Map<String, Object> metadata) {
        AuditEvent auditEvent = new AuditEvent(
                idGenerator.newId(),
                tenantContext.currentTenantId(),
                tenantContext.currentUserId(),
                action,
                entityType,
                entityId,
                UUID.fromString(currentCorrelationId()),
                Map.copyOf(metadata),
                clock.now());
        auditEventRepository.save(auditEvent);
    }

    private String currentCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return correlationId != null ? correlationId : UUID.randomUUID().toString();
    }
}
