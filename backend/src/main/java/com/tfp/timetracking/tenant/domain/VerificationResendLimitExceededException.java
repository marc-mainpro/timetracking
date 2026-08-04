package com.tfp.timetracking.tenant.domain;

import com.tfp.timetracking.shared.domain.DomainException;

/**
 * Se agotaron los reenvíos permitidos del correo de verificación de una
 * solicitud (RF-REG-003, T53-05).
 *
 * <p>Nunca llega al borde público: el endpoint de reenvío la captura y responde
 * igual que en el caso normal (RF-REG-005). Existe como excepción de dominio
 * para que la regla viva en el agregado y sea verificable en un test unitario.
 */
public final class VerificationResendLimitExceededException extends DomainException {

    public VerificationResendLimitExceededException(int maxResends) {
        super(
                "VERIFICATION_RESEND_LIMIT_EXCEEDED",
                "Se ha alcanzado el máximo de reenvíos del correo de verificación (" + maxResends + ")");
    }
}
