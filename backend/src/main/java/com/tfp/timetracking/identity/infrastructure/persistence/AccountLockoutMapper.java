package com.tfp.timetracking.identity.infrastructure.persistence;

import com.tfp.timetracking.identity.domain.AccountLockout;

final class AccountLockoutMapper {

    private AccountLockoutMapper() {}

    static AccountLockoutJpaEntity toJpaEntity(AccountLockout accountLockout) {
        return new AccountLockoutJpaEntity(
                accountLockout.userId(),
                accountLockout.tenantId(),
                accountLockout.failedAttempts(),
                accountLockout.lastFailedAttemptAt(),
                accountLockout.lockedUntil(),
                accountLockout.createdAt(),
                accountLockout.updatedAt());
    }

    static AccountLockout toDomain(AccountLockoutJpaEntity entity) {
        return AccountLockout.reconstitute(
                entity.getUserId(),
                entity.getTenantId(),
                entity.getFailedAttempts(),
                entity.getLastFailedAttemptAt(),
                entity.getLockedUntil(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
