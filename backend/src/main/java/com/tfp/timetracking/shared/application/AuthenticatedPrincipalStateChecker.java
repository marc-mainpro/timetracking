package com.tfp.timetracking.shared.application;

import java.util.UUID;

public interface AuthenticatedPrincipalStateChecker {

    default void ensureActivePrincipal(UUID tenantId, UUID userId) {
        ensureActivePrincipal(tenantId, userId, null);
    }

    void ensureActivePrincipal(UUID tenantId, UUID userId, UUID sessionId);
}
