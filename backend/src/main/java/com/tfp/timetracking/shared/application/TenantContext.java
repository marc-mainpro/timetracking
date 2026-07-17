package com.tfp.timetracking.shared.application;

import java.util.Set;
import java.util.UUID;

public interface TenantContext {

    UUID currentTenantId();

    UUID currentUserId();

    Set<String> currentRoles();
}
