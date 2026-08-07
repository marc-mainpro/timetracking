package com.tfp.timetracking.identity.infrastructure.persistence;

import com.tfp.timetracking.identity.domain.AccountLockout;
import com.tfp.timetracking.identity.domain.AccountLockoutRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AccountLockoutRepositoryAdapter implements AccountLockoutRepository {

    private final AccountLockoutJpaRepository jpaRepository;

    public AccountLockoutRepositoryAdapter(AccountLockoutJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AccountLockout save(AccountLockout accountLockout) {
        return AccountLockoutMapper.toDomain(jpaRepository.save(AccountLockoutMapper.toJpaEntity(accountLockout)));
    }

    @Override
    public Optional<AccountLockout> findByUserId(UUID tenantId, UUID userId) {
        return jpaRepository.findByTenantIdAndUserId(tenantId, userId).map(AccountLockoutMapper::toDomain);
    }
}
