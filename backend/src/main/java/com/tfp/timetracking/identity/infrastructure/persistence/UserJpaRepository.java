package com.tfp.timetracking.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data para {@link UserJpaEntity}. Uso interno del adaptador. */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    List<UserJpaEntity> findAllByTenantId(UUID tenantId);

    Page<UserJpaEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<UserJpaEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    /**
     * Listado paginado de los usuarios del tenant que tienen un rol concreto.
     *
     * <p>Usa {@code member of} y no un {@code join} sobre la coleccion de roles:
     * el join devolveria una fila por rol —obligando a un {@code distinct} que
     * Hibernate resuelve paginando en memoria—, mientras que {@code member of}
     * se traduce a un {@code exists} que deja una fila por usuario y permite a
     * Postgres aplicar el {@code limit/offset}.
     *
     * <p>El {@code countQuery} es explicito porque el derivado automaticamente
     * a partir de una consulta con {@code member of} no siempre coincide, y de
     * el dependen {@code totalElements} y {@code totalPages}.
     */
    @Query(
            value = """
                    select user
                    from UserJpaEntity user
                    where user.tenantId = :tenantId
                      and :role member of user.roles
                    """,
            countQuery = """
                    select count(user)
                    from UserJpaEntity user
                    where user.tenantId = :tenantId
                      and :role member of user.roles
                    """)
    Page<UserJpaEntity> findByTenantIdAndRole(
            @Param("tenantId") UUID tenantId, @Param("role") String role, Pageable pageable);

    /** Variante de {@link #findByTenantIdAndRole} acotada ademas por estado. */
    @Query(
            value = """
                    select user
                    from UserJpaEntity user
                    where user.tenantId = :tenantId
                      and user.status = :status
                      and :role member of user.roles
                    """,
            countQuery = """
                    select count(user)
                    from UserJpaEntity user
                    where user.tenantId = :tenantId
                      and user.status = :status
                      and :role member of user.roles
                    """)
    Page<UserJpaEntity> findByTenantIdAndStatusAndRole(
            @Param("tenantId") UUID tenantId,
            @Param("status") String status,
            @Param("role") String role,
            Pageable pageable);

    @Query("""
            select distinct user
            from UserJpaEntity user
            join user.roles role
            where user.tenantId = :tenantId
              and user.status = 'ACTIVE'
              and role = :role
            order by user.email asc
            """)
    List<UserJpaEntity> findActiveByRole(@Param("tenantId") UUID tenantId, @Param("role") String role);

    @Query(
            value = """
                    select u.id
                    from app_user u
                    join user_role ur on ur.user_id = u.id
                    where u.tenant_id = :tenantId
                      and u.status = 'ACTIVE'
                      and ur.role = 'TENANT_ADMIN'
                    order by u.id
                    for update
                    """,
            nativeQuery = true)
    List<UUID> lockActiveAdmins(@Param("tenantId") UUID tenantId);

    @Query("""
            select count(user)
            from UserJpaEntity user
            join user.roles role
            where user.tenantId = :tenantId
              and user.status = 'ACTIVE'
              and role = 'TENANT_ADMIN'
            """)
    long countActiveAdmins(@Param("tenantId") UUID tenantId);

    @Query("""
            select count(user)
            from UserJpaEntity user
            join user.roles role
            where user.tenantId = :tenantId
              and user.id <> :userId
              and user.status = 'ACTIVE'
              and role = 'TENANT_ADMIN'
            """)
    long countActiveAdminsExcludingUser(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);
}
