package com.tfp.timetracking.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLockoutJpaRepository extends JpaRepository<AccountLockoutJpaEntity, UUID> {

    Optional<AccountLockoutJpaEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
