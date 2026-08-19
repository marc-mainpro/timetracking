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

    /**
     * Listado paginado con todos los filtros resueltos en SQL.
     *
     * <p>El filtro por rol usa {@code exists} en vez de un {@code join} sobre la
     * coleccion de roles para evitar duplicados cuando un usuario acumula dos
     * roles; asi la base de datos pagina usuarios reales y no filas infladas por
     * la tabla intermedia.
     */
    @Query(
            value = """
                    select user
                    from UserJpaEntity user
                    where user.tenantId = :tenantId
                      and (:status is null or user.status = :status)
                      and (
                            :role is null
                            or exists (
                                select 1
                                from UserJpaEntity filtered
                                join filtered.roles role
                                where filtered = user
                                  and role = :role
                            )
                      )
                      and (
                            :query = ''
                            or lower(user.email) like concat('%', :query, '%')
                            or lower(concat(user.firstName, ' ', user.lastName)) like concat('%', :query, '%')
                      )
                    """,
            countQuery = """
                    select count(user)
                    from UserJpaEntity user
                    where user.tenantId = :tenantId
                      and (:status is null or user.status = :status)
                      and (
                            :role is null
                            or exists (
                                select 1
                                from UserJpaEntity filtered
                                join filtered.roles role
                                where filtered = user
                                  and role = :role
                            )
                      )
                      and (
                            :query = ''
                            or lower(user.email) like concat('%', :query, '%')
                            or lower(concat(user.firstName, ' ', user.lastName)) like concat('%', :query, '%')
                      )
                    """)
    Page<UserJpaEntity> findByTenantAndFilters(
            @Param("tenantId") UUID tenantId,
            @Param("status") String status,
            @Param("role") String role,
            @Param("query") String query,
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
