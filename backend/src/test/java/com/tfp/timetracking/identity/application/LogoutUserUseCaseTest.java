package com.tfp.timetracking.identity.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.shared.domain.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LogoutUserUseCaseTest {

    private final RefreshTokenRepository refreshTokenRepository = org.mockito.Mockito.mock(RefreshTokenRepository.class);
    private final RefreshTokenHasher refreshTokenHasher = org.mockito.Mockito.mock(RefreshTokenHasher.class);
    private final Clock clock = () -> Instant.parse("2026-01-15T10:00:00Z");

    @Test
    void revokesCurrentRefreshTokenOnLogout() {
        LogoutUserUseCase useCase = new LogoutUserUseCase(refreshTokenRepository, refreshTokenHasher, clock);
        RefreshToken refreshToken = RefreshToken.reconstitute(
                UUID.randomUUID(), UUID.randomUUID(), "hash", Instant.parse("2026-01-16T10:00:00Z"), null, null, clock.now());
        when(refreshTokenHasher.hash("opaque-token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(java.util.Optional.of(refreshToken));

        useCase.logout(new LogoutUserCommand("opaque-token"));

        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    void ignoresMissingRefreshToken() {
        LogoutUserUseCase useCase = new LogoutUserUseCase(refreshTokenRepository, refreshTokenHasher, clock);

        useCase.logout(new LogoutUserCommand(null));

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }
}
