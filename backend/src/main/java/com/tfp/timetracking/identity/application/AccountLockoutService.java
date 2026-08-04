package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.identity.domain.AccountLockout;
import com.tfp.timetracking.identity.domain.AccountLockoutRepository;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.shared.domain.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloqueo temporal de cuentas tras intentos fallidos (T30-04, RF-USR-008,
 * RS-008).
 *
 * <p><b>Por que {@code REQUIRES_NEW} en el registro de fallos:</b> un login
 * fallido termina lanzando {@link
 * com.tfp.timetracking.identity.domain.InvalidCredentialsException}, que hace
 * rollback de la transaccion del caso de uso. Si el incremento del contador y
 * su auditoria viajasen en esa misma transaccion se perderian con ella y el
 * bloqueo nunca llegaria a dispararse. Por eso el fallo se persiste en una
 * transaccion independiente que confirma antes de propagarse el error.
 */
@Service
public class AccountLockoutService {

    /** Accion de auditoria de cada intento de login fallido. */
    public static final String AUDIT_LOGIN_FAILED = "LOGIN_FAILED";

    /** Accion de auditoria del bloqueo efectivo de la cuenta. */
    public static final String AUDIT_ACCOUNT_LOCKED = "ACCOUNT_LOCKED";

    /** Accion de auditoria del intento contra una cuenta ya bloqueada. */
    public static final String AUDIT_LOGIN_BLOCKED = "LOGIN_ATTEMPT_WHILE_LOCKED";

    private static final String AUDIT_ENTITY_TYPE = "User";

    private final AccountLockoutRepository accountLockoutRepository;
    private final AuditRecorder auditRecorder;
    private final AuthenticationMetrics authenticationMetrics;
    private final Clock clock;
    private final AccountLockoutPolicy policy;

    public AccountLockoutService(
            AccountLockoutRepository accountLockoutRepository,
            AuditRecorder auditRecorder,
            AuthenticationMetrics authenticationMetrics,
            Clock clock,
            @Value("${auth.account-lockout.threshold:5}") int threshold,
            @Value("${auth.account-lockout.lock-duration:PT15M}") Duration lockDuration,
            @Value("${auth.account-lockout.failure-window:PT30M}") Duration failureWindow) {
        this.accountLockoutRepository = accountLockoutRepository;
        this.auditRecorder = auditRecorder;
        this.authenticationMetrics = authenticationMetrics;
        this.clock = clock;
        this.policy = new AccountLockoutPolicy(threshold, lockDuration, failureWindow);
    }

    public AccountLockoutPolicy policy() {
        return policy;
    }

    /** Indica si la cuenta del usuario esta bloqueada en este momento. */
    @Transactional(readOnly = true)
    public boolean isLocked(User user) {
        return accountLockoutRepository
                .findByUserId(user.tenantId(), user.id())
                .filter(lockout -> lockout.isLocked(clock))
                .isPresent();
    }

    /**
     * Registra un intento fallido en su propia transaccion y bloquea la cuenta
     * si se alcanza el umbral. Devuelve {@code true} si este intento ha
     * provocado el bloqueo.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerFailedAttempt(User user) {
        AccountLockout lockout = loadOrStart(user);
        boolean justLocked =
                lockout.registerFailure(policy.threshold(), policy.lockDuration(), policy.failureWindow(), clock);
        accountLockoutRepository.save(lockout);

        auditRecorder.record(
                user.tenantId(),
                user.id(),
                AUDIT_LOGIN_FAILED,
                AUDIT_ENTITY_TYPE,
                user.id(),
                auditMetadata(lockout));
        if (justLocked) {
            auditRecorder.record(
                    user.tenantId(),
                    user.id(),
                    AUDIT_ACCOUNT_LOCKED,
                    AUDIT_ENTITY_TYPE,
                    user.id(),
                    auditMetadata(lockout));
            authenticationMetrics.recordAccountLocked();
        }
        return justLocked;
    }

    /**
     * Deja constancia de un intento contra una cuenta ya bloqueada. No alarga
     * el bloqueo —si lo hiciera, un atacante podria mantener bloqueada la
     * cuenta de un tercero indefinidamente (denegacion de servicio dirigida)—
     * pero si se audita, porque es la senal de que el ataque sigue en curso.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerBlockedAttempt(User user) {
        auditRecorder.record(
                user.tenantId(),
                user.id(),
                AUDIT_LOGIN_BLOCKED,
                AUDIT_ENTITY_TYPE,
                user.id(),
                Map.of("threshold", policy.threshold()));
    }

    /**
     * Reinicia el contador tras una autenticacion correcta (RS-008). Participa
     * en la transaccion del caso de uso: si la emision de la sesion falla, el
     * reinicio tampoco debe darse por bueno.
     */
    @Transactional
    public void registerSuccessfulAttempt(User user) {
        accountLockoutRepository
                .findByUserId(user.tenantId(), user.id())
                .filter(lockout -> !lockout.isPristine())
                .ifPresent(lockout -> {
                    lockout.registerSuccess(clock);
                    accountLockoutRepository.save(lockout);
                });
    }

    private AccountLockout loadOrStart(User user) {
        return accountLockoutRepository
                .findByUserId(user.tenantId(), user.id())
                .orElseGet(() -> AccountLockout.start(user.tenantId(), user.id(), clock));
    }

    private Map<String, Object> auditMetadata(AccountLockout lockout) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("failedAttempts", lockout.failedAttempts());
        metadata.put("threshold", policy.threshold());
        if (lockout.lockedUntil() != null) {
            metadata.put("lockedUntil", lockout.lockedUntil().toString());
        }
        return metadata;
    }
}
