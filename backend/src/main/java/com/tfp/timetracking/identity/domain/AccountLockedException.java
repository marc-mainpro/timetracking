package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * La cuenta esta bloqueada temporalmente por acumulacion de intentos fallidos
 * (RF-USR-008, RS-008).
 *
 * <p>El mensaje no incluye ni el email ni la fecha exacta de desbloqueo: este
 * error solo se devuelve a quien ya ha demostrado conocer la contrasena
 * correcta, y aun asi no hay razon para filtrar mas de lo necesario. Ante
 * credenciales incorrectas contra una cuenta bloqueada se responde
 * {@link InvalidCredentialsException}, indistinguible de una cuenta inexistente
 * (anti-enumeracion, RS-008).
 */
public final class AccountLockedException extends DomainException {

    public AccountLockedException() {
        super("ACCOUNT_LOCKED", "La cuenta esta bloqueada temporalmente por intentos fallidos");
    }
}
