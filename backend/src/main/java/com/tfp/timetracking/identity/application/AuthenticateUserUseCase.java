package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.AccountLockedException;
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
    private final AccountLockoutService accountLockoutService;
    private final AuthenticationMetrics authenticationMetrics;
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
            AccountLockoutService accountLockoutService,
            AuthenticationMetrics authenticationMetrics,
            Clock clock,
            IdGenerator idGenerator,
            @Value("${auth.refresh-token.ttl:P14D}") Duration refreshTokenTtl) {
        this.accountLockoutService = accountLockoutService;
        this.authenticationMetrics = authenticationMetrics;
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

    /**
     * Autentica al usuario aplicando el bloqueo temporal por intentos fallidos
     * (RF-USR-008, RS-008).
     *
     * <p><b>Anti-enumeracion:</b> el error {@code ACCOUNT_LOCKED} solo se
     * devuelve a quien ademas ha acertado la contrasena. Un atacante que prueba
     * credenciales recibe siempre {@code INVALID_CREDENTIALS}, exactamente la
     * misma respuesta que ante un email inexistente, asi que no puede usar el
     * login para descubrir que cuentas existen ni cuales ha conseguido
     * bloquear. El usuario legitimo, que si conoce su contrasena, obtiene el
     * mensaje explicativo que necesita.
     *
     * <p>Por eso la contrasena se comprueba <b>antes</b> de decidir la
     * respuesta al bloqueo, y no se cortocircuita: el trabajo de BCrypt se hace
     * igual en ambos caminos.
     */
    @Transactional
    public AuthenticatedSession authenticate(AuthenticateUserCommand command) {
        User user = userRepository.findByEmail(Email.of(command.email())).orElse(null);
        if (user == null) {
            authenticationMetrics.recordLoginFailed(AuthenticationMetrics.REASON_UNKNOWN_EMAIL);
            throw new InvalidCredentialsException();
        }
        ensureTenantAndUserAreActive(user);

        boolean passwordMatches = passwordHasher.matches(command.password(), user.passwordHash());
        if (accountLockoutService.isLocked(user)) {
            accountLockoutService.registerBlockedAttempt(user);
            authenticationMetrics.recordLoginFailed(AuthenticationMetrics.REASON_LOCKED);
            if (passwordMatches) {
                throw new AccountLockedException();
            }
            throw new InvalidCredentialsException();
        }
        if (!passwordMatches) {
            accountLockoutService.registerFailedAttempt(user);
            authenticationMetrics.recordLoginFailed(AuthenticationMetrics.REASON_BAD_CREDENTIALS);
            throw new InvalidCredentialsException();
        }

        accountLockoutService.registerSuccessfulAttempt(user);
        authenticationMetrics.recordLoginSucceeded();
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
