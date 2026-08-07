package com.tfp.timetracking.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {

    Session save(Session session);

    Optional<Session> findById(UUID tenantId, UUID userId, UUID sessionId);

    Optional<Session> findById(UUID sessionId);

    List<Session> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<Session> findByUserId(UUID userId);
}
