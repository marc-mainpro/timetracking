package com.tfp.timetracking.identity.infrastructure.persistence;

import com.tfp.timetracking.identity.domain.RefreshToken;

final class RefreshTokenMapper {

    private RefreshTokenMapper() {}

    static RefreshTokenJpaEntity toJpaEntity(RefreshToken refreshToken) {
        return new RefreshTokenJpaEntity(
                refreshToken.id(),
                refreshToken.userId(),
                refreshToken.tokenHash(),
                refreshToken.expiresAt(),
                refreshToken.revokedAt(),
                refreshToken.replacedBy(),
                refreshToken.createdAt());
    }

    static RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return RefreshToken.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getReplacedBy(),
                entity.getCreatedAt());
    }
}
