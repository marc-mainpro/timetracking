package com.tfp.timetracking.identity.domain;

import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.Optional;
import java.util.List;
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

    /**
     * Via tenant-aware por defecto para consultas de negocio. Si el usuario no
     * pertenece al tenant indicado, debe devolver vacio aunque el id exista.
     */
    Optional<User> findById(UUID tenantId, UUID id);

    /**
     * Excepcion temporal documentada para flujos de autenticacion no ligados a
     * un TenantContext previo (p. ej. refresh token).
     */
    Optional<User> findById(UUID id);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);

    List<User> findAllByTenantId(UUID tenantId);

    PagedResult<User> findByTenant(UUID tenantId, UserStatus status, int page, int size);

    long countActiveAdmins(UUID tenantId);

    long countActiveAdminsExcludingUser(UUID tenantId, UUID userId);
}
