package com.tfp.timetracking.audit.application;

import java.util.Map;
import java.util.UUID;

public interface AuditRecorder {

    void record(String action, String entityType, UUID entityId, Map<String, Object> metadata);
}
