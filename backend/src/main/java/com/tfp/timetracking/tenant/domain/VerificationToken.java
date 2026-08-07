package com.tfp.timetracking.tenant.domain;

/**
 * Par «token en claro / hash del token» generado por
 * {@link VerificationTokenGenerator}.
 *
 * <p>El agregado solo persiste {@link #hash()} (RS-014, T53-05): el valor en
 * claro viaja una única vez, dentro del correo de verificación, y nunca se
 * almacena. Un volcado de la base de datos no permite construir un enlace de
 * verificación válido.
 *
 * @param value token en claro; solo debe salir del proceso dentro del cuerpo
 *     de un correo, nunca en un log ni en una respuesta HTTP
 * @param hash huella criptográfica del token, lo único que se persiste
 */
public record VerificationToken(String value, String hash) {

    public VerificationToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El token de verificacion es obligatorio");
        }
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("El hash del token de verificacion es obligatorio");
        }
    }
}
