package com.tfp.timetracking.identity.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetTokenJpaEntity token where token.tokenHash = :tokenHash")
    Optional<PasswordResetTokenJpaEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<PasswordResetTokenJpaEntity> findByTenantIdAndUserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID tenantId, UUID userId);
}
