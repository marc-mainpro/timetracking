package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.identity.domain.Session;
import com.tfp.timetracking.identity.domain.SessionRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeSessionUseCase {

    private final SessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantContext tenantContext;
    private final Clock clock;

    public RevokeSessionUseCase(
            SessionRepository sessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            TenantContext tenantContext,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    @Transactional
    public boolean revoke(UUID sessionId) {
        Session session = sessionRepository
                .findById(tenantContext.currentTenantId(), tenantContext.currentUserId(), sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesion no encontrada"));
        Instant now = clock.now();
        if (!session.isRevoked()) {
            session.revoke(now);
            sessionRepository.save(session);
        }
        for (RefreshToken refreshToken : refreshTokenRepository.findBySessionId(sessionId)) {
            if (!refreshToken.isRevoked()) {
                refreshToken.revoke(now);
                refreshTokenRepository.save(refreshToken);
            }
        }
        return sessionId.equals(tenantContext.currentSessionId());
    }
}
