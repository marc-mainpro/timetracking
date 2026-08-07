package com.tfp.timetracking.tenant.infrastructure;

import com.tfp.timetracking.tenant.domain.VerificationToken;
import com.tfp.timetracking.tenant.domain.VerificationTokenGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Implementación del puerto {@link VerificationTokenGenerator} (T53-05):
 * 32 bytes de {@link SecureRandom} codificados en Base64 URL-safe sin padding
 * (256 bits de entropía, seguro dentro de una URL) y hash SHA-256 en
 * hexadecimal para almacenar.
 *
 * <p>SHA-256 sin sal ni coste es <b>deliberado</b> y no es el error que sería en
 * una contraseña: el token ya es aleatorio de 256 bits, así que no hay
 * diccionario que probar y un hash lento solo penalizaría al servidor. Lo que
 * aporta el hash es que un volcado de la tabla no contenga tokens usables.
 */
@Component
public class Sha256VerificationTokenGenerator implements VerificationTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public VerificationToken generate() {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        return new VerificationToken(value, hash(value));
    }

    @Override
    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("El token es obligatorio");
        }
        return HexFormat.of().formatHex(digest().digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
