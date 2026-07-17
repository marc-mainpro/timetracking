package com.tfp.timetracking.audit.infrastructure;

import com.tfp.timetracking.audit.application.AuditRecorder;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NoOpAuditRecorder implements AuditRecorder {

    @Override
    public void record(String action, String entityType, UUID entityId, Map<String, Object> metadata) {
        // T603 implementara la persistencia real de auditoria.
    }
}
