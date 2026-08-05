package com.tfp.timetracking.identity.infrastructure.persistence;

import com.tfp.timetracking.identity.domain.PasswordResetToken;

final class PasswordResetTokenMapper {

    private PasswordResetTokenMapper() {}

    static PasswordResetTokenJpaEntity toJpaEntity(PasswordResetToken token) {
        return new PasswordResetTokenJpaEntity(
                token.id(), token.tenantId(), token.userId(), token.tokenHash(), token.expiresAt(), token.usedAt(), token.createdAt());
    }

    static PasswordResetToken toDomain(PasswordResetTokenJpaEntity entity) {
        return PasswordResetToken.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getUsedAt(),
                entity.getCreatedAt());
    }
}
