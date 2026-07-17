package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.InvalidCredentialsException;
import com.tfp.timetracking.identity.domain.InvalidRefreshTokenException;
import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.identity.domain.RefreshTokenReuseDetectedException;
import com.tfp.timetracking.identity.domain.TenantAccessRepository;
import com.tfp.timetracking.identity.domain.TenantInactiveException;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserInactiveException;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshSessionUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final AccessTokenGenerator accessTokenGenerator;
    private final UserRepository userRepository;
    private final TenantAccessRepository tenantAccessRepository;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final Duration refreshTokenTtl;

    public RefreshSessionUseCase(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenHasher refreshTokenHasher,
            RefreshTokenGenerator refreshTokenGenerator,
            AccessTokenGenerator accessTokenGenerator,
            UserRepository userRepository,
            TenantAccessRepository tenantAccessRepository,
            Clock clock,
            IdGenerator idGenerator,
            @Value("${auth.refresh-token.ttl:P14D}") Duration refreshTokenTtl) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenHasher = refreshTokenHasher;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.accessTokenGenerator = accessTokenGenerator;
        this.userRepository = userRepository;
        this.tenantAccessRepository = tenantAccessRepository;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional(noRollbackFor = {RefreshTokenReuseDetectedException.class, InvalidRefreshTokenException.class})
    public AuthenticatedSession refresh(RefreshSessionCommand command) {
        String tokenValue = validateRawToken(command.refreshToken());
        RefreshToken currentToken = refreshTokenRepository
                .findByTokenHashForUpdate(refreshTokenHasher.hash(tokenValue))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (currentToken.isRevoked()) {
            revokeAllTokens(currentToken.userId());
            throw new RefreshTokenReuseDetectedException();
        }

        Instant now = clock.now();
        if (currentToken.isExpiredAt(now)) {
            currentToken.revoke(now);
            refreshTokenRepository.save(currentToken);
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findById(currentToken.userId()).orElseThrow(InvalidCredentialsException::new);
        ensureTenantAndUserAreActive(user);

        String newRawRefreshToken = refreshTokenGenerator.generate();
        RefreshToken replacement = RefreshToken.issue(
                user.id(), refreshTokenHasher.hash(newRawRefreshToken), now.plus(refreshTokenTtl), clock, idGenerator);
        currentToken.rotateTo(replacement.id(), now);
        refreshTokenRepository.save(currentToken);
        refreshTokenRepository.save(replacement);

        IssuedAccessToken accessToken = accessTokenGenerator.generate(user);
        return new AuthenticatedSession(accessToken.value(), accessToken.expiresAt(), newRawRefreshToken);
    }

    private void revokeAllTokens(java.util.UUID userId) {
        Instant now = clock.now();
        for (RefreshToken refreshToken : refreshTokenRepository.findByUserId(userId)) {
            if (!refreshToken.isRevoked()) {
                refreshToken.revoke(now);
                refreshTokenRepository.save(refreshToken);
            }
        }
    }

    private void ensureTenantAndUserAreActive(User user) {
        if (!user.isActive()) {
            throw new UserInactiveException();
        }
        if (!tenantAccessRepository.isActive(user.tenantId())) {
            throw new TenantInactiveException();
        }
    }

    private String validateRawToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        return refreshToken;
    }
}
