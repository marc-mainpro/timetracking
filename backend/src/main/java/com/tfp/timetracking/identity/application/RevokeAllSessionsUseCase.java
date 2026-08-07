package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.identity.domain.Session;
import com.tfp.timetracking.identity.domain.SessionRepository;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeAllSessionsUseCase {

    private final SessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantContext tenantContext;
    private final Clock clock;

    public RevokeAllSessionsUseCase(
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
    public void revokeAllCurrentUserSessions() {
        Instant now = clock.now();
        for (Session session : sessionRepository.findByTenantIdAndUserId(tenantContext.currentTenantId(), tenantContext.currentUserId())) {
            if (!session.isRevoked()) {
                session.revoke(now);
                sessionRepository.save(session);
            }
        }
        for (RefreshToken refreshToken : refreshTokenRepository.findByUserId(tenantContext.currentUserId())) {
            if (!refreshToken.isRevoked()) {
                refreshToken.revoke(now);
                refreshTokenRepository.save(refreshToken);
            }
        }
    }
}
