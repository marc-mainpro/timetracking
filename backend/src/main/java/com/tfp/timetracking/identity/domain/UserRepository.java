package com.tfp.timetracking.identity.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de dominio para persistir y recuperar {@link User} (CONTEXT-GLOBAL
 * §4: puertos definidos en dominio, implementados en infraestructura).
 *
 * <p>El email del usuario es unico globalmente para evitar ambiguedad en la
 * autenticacion por email+password (ADR-0008).
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);
}
