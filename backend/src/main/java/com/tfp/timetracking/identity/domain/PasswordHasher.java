package com.tfp.timetracking.identity.domain;

/**
 * Puerto de dominio para hashear contraseñas en claro antes de persistirlas
 * en {@link User#passwordHash()} (CONTEXT-GLOBAL §3: BCrypt via
 * {@code DelegatingPasswordEncoder}). El dominio nunca conoce el algoritmo
 * concreto; la implementacion vive en infraestructura.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
