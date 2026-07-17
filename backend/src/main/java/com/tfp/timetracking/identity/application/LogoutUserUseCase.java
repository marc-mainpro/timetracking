package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.shared.domain.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutUserUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final Clock clock;

    public LogoutUserUseCase(
            RefreshTokenRepository refreshTokenRepository, RefreshTokenHasher refreshTokenHasher, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
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
        if (!refreshToken.isRevoked()) {
            refreshToken.revoke(clock.now());
            refreshTokenRepository.save(refreshToken);
        }
    }
}
