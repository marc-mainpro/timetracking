package com.tfp.timetracking.tenant.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data para {@link TenantRegistrationJpaEntity}. Uso interno del adaptador. */
interface TenantRegistrationJpaRepository extends JpaRepository<TenantRegistrationJpaEntity, UUID> {

    Optional<TenantRegistrationJpaEntity> findByVerificationTokenHash(String verificationTokenHash);

    List<TenantRegistrationJpaEntity> findByEmailAndStatusInOrderByCreatedAtDesc(String email, List<String> statuses);

    Page<TenantRegistrationJpaEntity> findByStatus(String status, Pageable pageable);

    @Query("select count(r) from TenantRegistrationJpaEntity r where r.ipHash = :ipHash and r.createdAt >= :since")
    long countByIpHashSince(@Param("ipHash") String ipHash, @Param("since") Instant since);

    @Query("select count(r) from TenantRegistrationJpaEntity r where r.email = :email and r.createdAt >= :since")
    long countByEmailSince(@Param("email") String email, @Param("since") Instant since);
}
