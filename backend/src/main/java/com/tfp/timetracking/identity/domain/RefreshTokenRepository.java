package com.tfp.timetracking.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    List<RefreshToken> findByUserId(UUID userId);
}
