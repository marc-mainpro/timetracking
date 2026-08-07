package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.AccountLockedException;
import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.InvalidCredentialsException;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.identity.domain.Session;
import com.tfp.timetracking.identity.domain.SessionRepository;
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
public class AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final TenantAccessRepository tenantAccessRepository;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRepository sessionRepository;
    private final AccessTokenGenerator accessTokenGenerator;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AccountLockoutService accountLockoutService;
    private final AuthenticationMetrics authenticationMetrics;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final Duration refreshTokenTtl;

    /**
     * Hash de descarte contra el que se compara cuando el email no existe, para
     * que el coste de BCrypt —y por tanto el tiempo de respuesta— sea el mismo
     * exista la cuenta o no. Se calcula al arrancar en vez de incrustar un hash
     * literal en el codigo: un literal con forma de hash es indistinguible de
     * un secreto filtrado para cualquier escaner.
     */
    private final String timingEqualisationHash;

    public AuthenticateUserUseCase(
            UserRepository userRepository,
            TenantAccessRepository tenantAccessRepository,
            PasswordHasher passwordHasher,
            RefreshTokenRepository refreshTokenRepository,
            SessionRepository sessionRepository,
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
        this.sessionRepository = sessionRepository;
        this.accessTokenGenerator = accessTokenGenerator;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenHasher = refreshTokenHasher;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.refreshTokenTtl = refreshTokenTtl;
        this.timingEqualisationHash = passwordHasher.hash("timing-equalisation-placeholder");
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
     * respuesta, y no se cortocircuita: el trabajo de BCrypt se hace igual en
     * todos los caminos.
     *
     * <p>El mismo criterio se aplica al estado de la cuenta y del tenant
     * (T160-02). Comprobarlos antes de la contrasena convertia el login en un
     * oraculo de existencia: un email desconocido respondia
     * {@code INVALID_CREDENTIALS} y uno real desactivado
     * {@code USER_INACTIVE}, asi que bastaba mirar el codigo de error para
     * saber que cuentas existen. Ahora ese estado solo se revela a quien ya ha
     * acertado la contrasena, es decir, a la persona duena de la cuenta, que es
     * quien necesita saber por que no puede entrar.
     *
     * <p>Y con un email inexistente se ejecuta igualmente una comparacion de
     * hash contra un valor de descarte: sin ella, la respuesta llegaba sin
     * pasar por BCrypt y el <b>tiempo</b> de respuesta delataba que ese correo
     * no existe, aunque el cuerpo fuese identico.
     */
    @Transactional
    public AuthenticatedSession authenticate(AuthenticateUserCommand command) {
        User user = userRepository.findByEmail(Email.of(command.email())).orElse(null);
        if (user == null) {
            passwordHasher.matches(command.password(), timingEqualisationHash);
            authenticationMetrics.recordLoginFailed(AuthenticationMetrics.REASON_UNKNOWN_EMAIL);
            throw new InvalidCredentialsException();
        }

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

        // Con la contrasena ya acertada: quien esta al otro lado es el dueno de
        // la cuenta y merece saber por que no puede entrar.
        ensureTenantAndUserAreActive(user);

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
        Instant expiresAt = clock.now().plus(refreshTokenTtl);
        Session session = Session.start(user.id(), user.tenantId(), expiresAt, clock, idGenerator);
        sessionRepository.save(session);
        IssuedAccessToken accessToken = accessTokenGenerator.generate(user, session.id());
        String rawRefreshToken = refreshTokenGenerator.generate();
        RefreshToken refreshToken = RefreshToken.issue(
                session.id(),
                user.id(),
                refreshTokenHasher.hash(rawRefreshToken),
                expiresAt,
                clock,
                idGenerator);
        refreshTokenRepository.save(refreshToken);
        return new AuthenticatedSession(accessToken.value(), accessToken.expiresAt(), rawRefreshToken);
    }
}
