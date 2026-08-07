package com.tfp.timetracking.identity.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.identity.domain.Session;
import com.tfp.timetracking.identity.domain.SessionRepository;
import com.tfp.timetracking.shared.domain.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LogoutUserUseCaseTest {

    private final RefreshTokenRepository refreshTokenRepository = org.mockito.Mockito.mock(RefreshTokenRepository.class);
    private final SessionRepository sessionRepository = org.mockito.Mockito.mock(SessionRepository.class);
    private final RefreshTokenHasher refreshTokenHasher = org.mockito.Mockito.mock(RefreshTokenHasher.class);
    private final Clock clock = () -> Instant.parse("2026-01-15T10:00:00Z");

    @Test
    void revokesCurrentRefreshTokenOnLogout() {
        LogoutUserUseCase useCase = new LogoutUserUseCase(refreshTokenRepository, sessionRepository, refreshTokenHasher, clock);
        Session session = Session.reconstitute(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                clock.now().minusSeconds(60),
                clock.now().minusSeconds(30),
                Instant.parse("2026-01-16T10:00:00Z"),
                null,
                null,
                null);
        RefreshToken refreshToken = RefreshToken.reconstitute(
                UUID.randomUUID(),
                session.id(),
                session.userId(),
                "hash",
                Instant.parse("2026-01-16T10:00:00Z"),
                null,
                null,
                clock.now());
        when(refreshTokenHasher.hash("opaque-token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(java.util.Optional.of(refreshToken));
        when(sessionRepository.findById(session.id())).thenReturn(java.util.Optional.of(session));
        when(refreshTokenRepository.findBySessionId(session.id())).thenReturn(java.util.List.of(refreshToken));

        useCase.logout(new LogoutUserCommand("opaque-token"));

        verify(sessionRepository).save(session);
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    void ignoresMissingRefreshToken() {
        LogoutUserUseCase useCase = new LogoutUserUseCase(refreshTokenRepository, sessionRepository, refreshTokenHasher, clock);

        useCase.logout(new LogoutUserCommand(null));

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }
}
