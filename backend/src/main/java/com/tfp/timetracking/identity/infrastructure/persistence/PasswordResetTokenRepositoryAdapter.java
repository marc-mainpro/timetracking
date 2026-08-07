package com.tfp.timetracking.identity.infrastructure.persistence;

import com.tfp.timetracking.identity.domain.PasswordResetToken;
import com.tfp.timetracking.identity.domain.PasswordResetTokenRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository jpaRepository;

    public PasswordResetTokenRepositoryAdapter(PasswordResetTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        return PasswordResetTokenMapper.toDomain(jpaRepository.save(PasswordResetTokenMapper.toJpaEntity(token)));
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHashForUpdate(String tokenHash) {
        return jpaRepository.findByTokenHashForUpdate(tokenHash).map(PasswordResetTokenMapper::toDomain);
    }

    @Override
    public List<PasswordResetToken> findUnusedByTenantIdAndUserId(UUID tenantId, UUID userId) {
        return jpaRepository.findByTenantIdAndUserIdAndUsedAtIsNullOrderByCreatedAtDesc(tenantId, userId).stream()
                .map(PasswordResetTokenMapper::toDomain)
                .toList();
    }
}
