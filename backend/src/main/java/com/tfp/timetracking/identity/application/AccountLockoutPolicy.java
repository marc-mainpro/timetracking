package com.tfp.timetracking.identity.application;

import java.time.Duration;

/**
 * Politica de bloqueo temporal de cuentas (RS-008). Umbral y duracion son
 * configurables (`config/account-lockout.yml`), que es justamente lo que exige
 * la ficha T30-04.
 *
 * @param threshold intentos fallidos consecutivos que disparan el bloqueo
 * @param lockDuration cuanto permanece bloqueada la cuenta
 * @param failureWindow ventana en la que los fallos se consideran consecutivos;
 *     pasado ese tiempo sin fallar, el contador vuelve a cero
 */
public record AccountLockoutPolicy(int threshold, Duration lockDuration, Duration failureWindow) {

    public AccountLockoutPolicy {
        if (threshold < 1) {
            throw new IllegalArgumentException("auth.account-lockout.threshold debe ser al menos 1");
        }
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("auth.account-lockout.lock-duration debe ser positiva");
        }
        if (failureWindow == null || failureWindow.isNegative() || failureWindow.isZero()) {
            throw new IllegalArgumentException("auth.account-lockout.failure-window debe ser positiva");
        }
    }
}
