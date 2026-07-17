package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.InvalidCredentialsException;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.identity.domain.TenantAccessRepository;
import com.tfp.timetracking.identity.domain.TenantInactiveException;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserInactiveException;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final TenantAccessRepository tenantAccessRepository;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenGenerator accessTokenGenerator;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final Duration refreshTokenTtl;

    public AuthenticateUserUseCase(
            UserRepository userRepository,
            TenantAccessRepository tenantAccessRepository,
            PasswordHasher passwordHasher,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenGenerator accessTokenGenerator,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenHasher refreshTokenHasher,
            Clock clock,
            IdGenerator idGenerator,
            @Value("${auth.refresh-token.ttl:P14D}") Duration refreshTokenTtl) {
        this.userRepository = userRepository;
        this.tenantAccessRepository = tenantAccessRepository;
        this.passwordHasher = passwordHasher;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenGenerator = accessTokenGenerator;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenHasher = refreshTokenHasher;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public AuthenticatedSession authenticate(AuthenticateUserCommand command) {
        User user = userRepository.findByEmail(Email.of(command.email())).orElseThrow(InvalidCredentialsException::new);
        ensureTenantAndUserAreActive(user);
        if (!passwordHasher.matches(command.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueSession(user);
    }

    private void ensureTenantAndUserAreActive(User user) {
        if (!user.isActive()) {
            throw new UserInactiveException();
        }
        if (!tenantAccessRepository.isActive(user.tenantId())) {
            throw new TenantInactiveException();
        }
    }

    AuthenticatedSession issueSession(User user) {
        IssuedAccessToken accessToken = accessTokenGenerator.generate(user);
        String rawRefreshToken = refreshTokenGenerator.generate();
        RefreshToken refreshToken = RefreshToken.issue(
                user.id(),
                refreshTokenHasher.hash(rawRefreshToken),
                clock.now().plus(refreshTokenTtl),
                clock,
                idGenerator);
        refreshTokenRepository.save(refreshToken);
        return new AuthenticatedSession(accessToken.value(), accessToken.expiresAt(), rawRefreshToken);
    }
}
