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

    /**
     * Usuarios activos de un tenant que tienen un rol concreto (T170-01).
     *
     * <p>La usa el fan-out de notificaciones, que dirige un aviso a un rol y no
     * a una persona. Filtra por {@code ACTIVE} en la consulta y no en memoria:
     * un tenant grande tiene muchos usuarios y solo interesan los que podrian
     * atender el aviso.
     */
    List<User> findActiveByRole(UUID tenantId, Role role);

    /**
     * Listado paginado de los usuarios de un tenant.
     *
     * @param status estado por el que acotar, o {@code null} para no acotar
     * @param role rol que debe tener el usuario, o {@code null} para no acotar.
     *     El filtro se resuelve en la consulta y no en memoria: hacerlo despues
     *     de paginar daria paginas de tamano variable y un {@code totalElements}
     *     que no corresponde a lo que se devuelve.
     * @param query texto libre para buscar por correo o nombre completo, o
     *     {@code null} para no acotar.
     */
    PagedResult<User> findByTenant(UUID tenantId, UserStatus status, Role role, String query, int page, int size);

    void lockActiveAdmins(UUID tenantId);

    long countActiveAdmins(UUID tenantId);

    long countActiveAdminsExcludingUser(UUID tenantId, UUID userId);
}
