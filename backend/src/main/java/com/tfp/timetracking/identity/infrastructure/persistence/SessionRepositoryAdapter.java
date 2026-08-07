package com.tfp.timetracking.identity.infrastructure.persistence;

import com.tfp.timetracking.identity.domain.Session;
import com.tfp.timetracking.identity.domain.SessionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepositoryAdapter implements SessionRepository {

    private final SessionJpaRepository jpaRepository;

    public SessionRepositoryAdapter(SessionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Session save(Session session) {
        return SessionMapper.toDomain(jpaRepository.save(SessionMapper.toJpaEntity(session)));
    }

    @Override
    public Optional<Session> findById(UUID tenantId, UUID userId, UUID sessionId) {
        return jpaRepository.findByIdAndTenantIdAndUserId(sessionId, tenantId, userId).map(SessionMapper::toDomain);
    }

    @Override
    public Optional<Session> findById(UUID sessionId) {
        return jpaRepository.findById(sessionId).map(SessionMapper::toDomain);
    }

    @Override
    public List<Session> findByTenantIdAndUserId(UUID tenantId, UUID userId) {
        return jpaRepository.findByTenantIdAndUserIdOrderByLastUsedAtDesc(tenantId, userId).stream()
                .map(SessionMapper::toDomain)
                .toList();
    }

    @Override
    public List<Session> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream().map(SessionMapper::toDomain).toList();
    }
}
