package com.tfp.timetracking.identity.infrastructure.security;

import com.tfp.timetracking.identity.domain.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Implementacion de infraestructura del puerto {@link PasswordHasher} usando
 * BCrypt (CONTEXT-GLOBAL §3: "Passwords: BCrypt (DelegatingPasswordEncoder
 * por defecto de Spring)"). Se usa {@link BCryptPasswordEncoder} directamente
 * porque este puerto solo hashea (nunca compara), evitando acoplar el
 * dominio al formato de codificacion delegada que usara el modulo de login
 * (T204).
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }
}
