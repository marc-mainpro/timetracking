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
