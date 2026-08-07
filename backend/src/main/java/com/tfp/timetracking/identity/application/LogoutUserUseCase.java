package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.identity.domain.Session;
import com.tfp.timetracking.identity.domain.SessionRepository;
import com.tfp.timetracking.shared.domain.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutUserUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRepository sessionRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final Clock clock;

    public LogoutUserUseCase(
            RefreshTokenRepository refreshTokenRepository,
            SessionRepository sessionRepository,
            RefreshTokenHasher refreshTokenHasher,
            Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionRepository = sessionRepository;
        this.refreshTokenHasher = refreshTokenHasher;
        this.clock = clock;
    }

    @Transactional
    public void logout(LogoutUserCommand command) {
        if (command.refreshToken() == null || command.refreshToken().isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(refreshTokenHasher.hash(command.refreshToken())).ifPresent(this::revokeIfNeeded);
    }

    private void revokeIfNeeded(RefreshToken refreshToken) {
        Instant now = clock.now();
        if (refreshToken.sessionId() == null) {
            if (!refreshToken.isRevoked()) {
                refreshToken.revoke(now);
                refreshTokenRepository.save(refreshToken);
            }
            return;
        }
        sessionRepository.findById(refreshToken.sessionId()).ifPresent(session -> revokeSession(session, now));
        for (RefreshToken tokenInSession : refreshTokenRepository.findBySessionId(refreshToken.sessionId())) {
            if (!tokenInSession.isRevoked()) {
                tokenInSession.revoke(now);
                refreshTokenRepository.save(tokenInSession);
            }
        }
    }

    private void revokeSession(Session session, Instant now) {
        if (!session.isRevoked()) {
            session.revoke(now);
            sessionRepository.save(session);
        }
    }
}
