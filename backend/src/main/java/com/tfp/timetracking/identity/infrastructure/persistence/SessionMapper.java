package com.tfp.timetracking.identity.infrastructure.persistence;

import com.tfp.timetracking.identity.domain.Session;

final class SessionMapper {

    private SessionMapper() {}

    static SessionJpaEntity toJpaEntity(Session session) {
        return new SessionJpaEntity(
                session.id(),
                session.userId(),
                session.tenantId(),
                session.createdAt(),
                session.lastUsedAt(),
                session.expiresAt(),
                session.revokedAt(),
                session.userAgentHash(),
                session.ipHash());
    }

    static Session toDomain(SessionJpaEntity entity) {
        return Session.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getTenantId(),
                entity.getCreatedAt(),
                entity.getLastUsedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getUserAgentHash(),
                entity.getIpHash());
    }
}
