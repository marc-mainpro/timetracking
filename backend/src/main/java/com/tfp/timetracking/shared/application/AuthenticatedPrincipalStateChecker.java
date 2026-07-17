package com.tfp.timetracking.shared.application;

import java.util.UUID;

public interface AuthenticatedPrincipalStateChecker {

    void ensureActivePrincipal(UUID tenantId, UUID userId);
}
