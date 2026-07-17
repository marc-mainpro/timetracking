package com.tfp.timetracking.identity.infrastructure.persistence;

import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;

    public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return RefreshTokenMapper.toDomain(jpaRepository.save(RefreshTokenMapper.toJpaEntity(refreshToken)));
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(RefreshTokenMapper::toDomain);
    }

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return jpaRepository.findByTokenHashForUpdate(tokenHash).map(RefreshTokenMapper::toDomain);
    }

    @Override
    public List<RefreshToken> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream().map(RefreshTokenMapper::toDomain).toList();
    }
}
