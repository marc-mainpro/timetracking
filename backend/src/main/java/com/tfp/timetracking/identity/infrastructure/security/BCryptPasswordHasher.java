package com.tfp.timetracking.identity.infrastructure.security;

import com.tfp.timetracking.identity.domain.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Implementacion de infraestructura del puerto {@link PasswordHasher} usando
 * BCrypt (CONTEXT-GLOBAL §3: "Passwords: BCrypt (DelegatingPasswordEncoder
 * por defecto de Spring)").
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
