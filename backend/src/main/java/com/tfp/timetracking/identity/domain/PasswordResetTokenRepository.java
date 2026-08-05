package com.tfp.timetracking.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHashForUpdate(String tokenHash);

    List<PasswordResetToken> findUnusedByTenantIdAndUserId(UUID tenantId, UUID userId);
}
