package com.tfp.timetracking.identity.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Metricas Micrometer de autenticacion (T30-04, T140), expuestas por Actuator
 * en {@code /actuator/metrics}:
 *
 * <ul>
 *   <li>{@code auth.login.failed} (contador): intentos de login rechazados,
 *       incluidos los de emails inexistentes. Etiqueta {@code reason} con
 *       {@code bad_credentials}, {@code unknown_email} o {@code locked}.
 *   <li>{@code auth.login.succeeded} (contador): logins completados.
 *   <li>{@code auth.accounts.locked} (contador): bloqueos temporales aplicados.
 * </ul>
 *
 * <p>Deliberadamente <b>sin</b> etiqueta de tenant, usuario ni IP: serian de
 * cardinalidad no acotada y ademas convertirian una serie temporal de metricas
 * —que no tiene control de acceso por tenant— en una fuente de enumeracion de
 * usuarios. El detalle por usuario esta en la auditoria, que si esta
 * tenant-scoped.
 */
@Component
public class AuthenticationMetrics {

    /** Credenciales incorrectas contra un usuario existente. */
    public static final String REASON_BAD_CREDENTIALS = "bad_credentials";

    /** El email no corresponde a ningun usuario. */
    public static final String REASON_UNKNOWN_EMAIL = "unknown_email";

    /** Intento contra una cuenta bloqueada temporalmente. */
    public static final String REASON_LOCKED = "locked";

    private final MeterRegistry registry;
    private final Counter succeeded;
    private final Counter locked;

    public AuthenticationMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.succeeded = Counter.builder("auth.login.succeeded")
                .description("Logins completados con exito")
                .register(registry);
        this.locked = Counter.builder("auth.accounts.locked")
                .description("Bloqueos temporales de cuenta aplicados tras superar el umbral de intentos fallidos")
                .register(registry);
        // Pre-registra las series de fallo para que existan con valor 0 antes
        // del primer incidente: un panel o alerta sobre una metrica ausente no
        // se puede distinguir de un sistema sano.
        failed(REASON_BAD_CREDENTIALS);
        failed(REASON_UNKNOWN_EMAIL);
        failed(REASON_LOCKED);
    }

    public void recordLoginFailed(String reason) {
        failed(reason).increment();
    }

    public void recordLoginSucceeded() {
        succeeded.increment();
    }

    public void recordAccountLocked() {
        locked.increment();
    }

    private Counter failed(String reason) {
        return Counter.builder("auth.login.failed")
                .description("Intentos de login rechazados")
                .tag("reason", reason)
                .register(registry);
    }
}
