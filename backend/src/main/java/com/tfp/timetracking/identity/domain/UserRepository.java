package com.tfp.timetracking.identity.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de dominio para persistir y recuperar {@link User} (CONTEXT-GLOBAL
 * §4: puertos definidos en dominio, implementados en infraestructura).
 *
 * <p>Todas las consultas de negocio se acotan por {@code tenantId}
 * (CONTEXT-GLOBAL §5): el email es unico dentro de un tenant, no global.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByTenantIdAndEmail(UUID tenantId, Email email);

    boolean existsByTenantIdAndEmail(UUID tenantId, Email email);
}
