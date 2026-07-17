package com.tfp.timetracking.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.identity.domain.InvalidRefreshTokenException;
import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.identity.domain.RefreshTokenReuseDetectedException;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.TenantAccessRepository;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefreshSessionUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final RefreshTokenRepository refreshTokenRepository = org.mockito.Mockito.mock(RefreshTokenRepository.class);
    private final RefreshTokenHasher refreshTokenHasher = org.mockito.Mockito.mock(RefreshTokenHasher.class);
    private final RefreshTokenGenerator refreshTokenGenerator = org.mockito.Mockito.mock(RefreshTokenGenerator.class);
    private final AccessTokenGenerator accessTokenGenerator = org.mockito.Mockito.mock(AccessTokenGenerator.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final TenantAccessRepository tenantAccessRepository = org.mockito.Mockito.mock(TenantAccessRepository.class);
    private final Clock clock = () -> NOW;
    private final IdGenerator idGenerator = () -> UUID.fromString("99999999-9999-9999-9999-999999999999");

    private RefreshSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RefreshSessionUseCase(
                refreshTokenRepository,
                refreshTokenHasher,
                refreshTokenGenerator,
                accessTokenGenerator,
                userRepository,
                tenantAccessRepository,
                clock,
                idGenerator,
                Duration.ofDays(14));
    }

    @Test
    void rotatesRefreshTokenAndReturnsNewSession() {
        User user = activeUser();
        RefreshToken current = RefreshToken.reconstitute(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                user.id(),
                "old-hash",
                NOW.plusSeconds(300),
                null,
                null,
                NOW.minusSeconds(60));
        when(refreshTokenHasher.hash("old-token")).thenReturn("old-hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("old-hash")).thenReturn(java.util.Optional.of(current));
        when(userRepository.findById(user.id())).thenReturn(java.util.Optional.of(user));
        when(tenantAccessRepository.isActive(user.tenantId())).thenReturn(true);
        when(refreshTokenGenerator.generate()).thenReturn("new-token");
        when(refreshTokenHasher.hash("new-token")).thenReturn("new-hash");
        when(accessTokenGenerator.generate(user)).thenReturn(new IssuedAccessToken("jwt", NOW.plusSeconds(900)));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthenticatedSession session = useCase.refresh(new RefreshSessionCommand("old-token"));

        assertThat(session.refreshToken()).isEqualTo("new-token");
        assertThat(current.isRevoked()).isTrue();
        verify(refreshTokenRepository, atLeastOnce()).save(any(RefreshToken.class));
    }

    @Test
    void reusedRefreshTokenRevokesUserChain() {
        UUID userId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        RefreshToken reused = RefreshToken.reconstitute(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                userId,
                "old-hash",
                NOW.plusSeconds(300),
                NOW.minusSeconds(5),
                UUID.randomUUID(),
                NOW.minusSeconds(60));
        RefreshToken sibling = RefreshToken.reconstitute(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                userId,
                "sibling-hash",
                NOW.plusSeconds(300),
                null,
                null,
                NOW.minusSeconds(30));
        when(refreshTokenHasher.hash("reused-token")).thenReturn("old-hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("old-hash")).thenReturn(java.util.Optional.of(reused));
        when(refreshTokenRepository.findByUserId(userId)).thenReturn(List.of(reused, sibling));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> useCase.refresh(new RefreshSessionCommand("reused-token")))
                .isInstanceOf(RefreshTokenReuseDetectedException.class);

        assertThat(sibling.isRevoked()).isTrue();
        verify(refreshTokenRepository).findByUserId(userId);
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        RefreshToken expired = RefreshToken.reconstitute(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                "old-hash",
                NOW.minusSeconds(1),
                null,
                null,
                NOW.minusSeconds(60));
        when(refreshTokenHasher.hash("expired-token")).thenReturn("old-hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("old-hash")).thenReturn(java.util.Optional.of(expired));

        assertThatThrownBy(() -> useCase.refresh(new RefreshSessionCommand("expired-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    private User activeUser() {
        return User.reconstitute(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "jane@example.com",
                "hash",
                "Jane",
                "Doe",
                UserStatus.ACTIVE,
                Set.of(Role.EMPLOYEE),
                NOW,
                NOW);
    }
}
