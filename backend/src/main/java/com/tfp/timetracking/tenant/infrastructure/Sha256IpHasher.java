package com.tfp.timetracking.tenant.infrastructure;

import com.tfp.timetracking.tenant.domain.IpHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link IpHasher}: SHA-256 sobre {@code pepper + ip}.
 *
 * <p>El «pepper» no es decorativo. El espacio de direcciones IPv4 tiene 2^32
 * elementos: un SHA-256 sin sal se invierte por fuerza bruta en minutos, así que
 * guardar {@code sha256(ip)} sería guardar la IP con pasos extra. Con un pepper
 * secreto por despliegue ({@code registration.ip-hash-pepper}) la huella deja de
 * ser invertible por quien solo tenga la base de datos, y sigue sirviendo para
 * comparar orígenes entre sí, que es lo único que pide RF-REG-003.
 *
 * <p>Si no se configura, se genera uno aleatorio al arrancar: seguro por
 * defecto, a costa de que los recuentos por IP no sobrevivan a un reinicio ni se
 * compartan entre instancias. En producción debe fijarse por entorno.
 */
@Component
public class Sha256IpHasher implements IpHasher {

    private final String pepper;

    public Sha256IpHasher(@Value("${registration.ip-hash-pepper:}") String configuredPepper) {
        this.pepper = configuredPepper == null || configuredPepper.isBlank() ? randomPepper() : configuredPepper;
    }

    @Override
    public String hash(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return null;
        }
        byte[] input = (pepper + '|' + clientIp.trim()).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(digest().digest(input));
    }

    private static String randomPepper() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
