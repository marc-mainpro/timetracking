package com.tfp.timetracking.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.identity.domain.InvalidCredentialsException;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.TenantAccessRepository;
import com.tfp.timetracking.identity.domain.TenantInactiveException;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserInactiveException;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticateUserUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final TenantAccessRepository tenantAccessRepository = org.mockito.Mockito.mock(TenantAccessRepository.class);
    private final PasswordHasher passwordHasher = org.mockito.Mockito.mock(PasswordHasher.class);
    private final RefreshTokenRepository refreshTokenRepository = org.mockito.Mockito.mock(RefreshTokenRepository.class);
    private final AccessTokenGenerator accessTokenGenerator = org.mockito.Mockito.mock(AccessTokenGenerator.class);
    private final RefreshTokenGenerator refreshTokenGenerator = org.mockito.Mockito.mock(RefreshTokenGenerator.class);
    private final RefreshTokenHasher refreshTokenHasher = org.mockito.Mockito.mock(RefreshTokenHasher.class);
    private final Clock clock = () -> NOW;
    private final IdGenerator idGenerator = () -> UUID.fromString("33333333-3333-3333-3333-333333333333");

    private AuthenticateUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AuthenticateUserUseCase(
                userRepository,
                tenantAccessRepository,
                passwordHasher,
                refreshTokenRepository,
                accessTokenGenerator,
                refreshTokenGenerator,
                refreshTokenHasher,
                clock,
                idGenerator,
                Duration.ofDays(14));
    }

    @Test
    void authenticatesActiveUserAndPersistsRefreshToken() {
        User user = activeUser(UserStatus.ACTIVE);
        when(userRepository.findByEmail(any())).thenReturn(java.util.Optional.of(user));
        when(tenantAccessRepository.isActive(user.tenantId())).thenReturn(true);
        when(passwordHasher.matches("secret", "hash")).thenReturn(true);
        when(accessTokenGenerator.generate(user)).thenReturn(new IssuedAccessToken("jwt", NOW.plusSeconds(900)));
        when(refreshTokenGenerator.generate()).thenReturn("opaque-refresh");
        when(refreshTokenHasher.hash("opaque-refresh")).thenReturn("refresh-hash");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthenticatedSession session = useCase.authenticate(new AuthenticateUserCommand("jane@example.com", "secret"));

        assertThat(session.accessToken()).isEqualTo("jwt");
        assertThat(session.refreshToken()).isEqualTo("opaque-refresh");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void rejectsInactiveUser() {
        User user = activeUser(UserStatus.INACTIVE);
        when(userRepository.findByEmail(any())).thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() -> useCase.authenticate(new AuthenticateUserCommand("jane@example.com", "secret")))
                .isInstanceOf(UserInactiveException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rejectsInactiveTenant() {
        User user = activeUser(UserStatus.ACTIVE);
        when(userRepository.findByEmail(any())).thenReturn(java.util.Optional.of(user));
        when(tenantAccessRepository.isActive(user.tenantId())).thenReturn(false);

        assertThatThrownBy(() -> useCase.authenticate(new AuthenticateUserCommand("jane@example.com", "secret")))
                .isInstanceOf(TenantInactiveException.class);
    }

    @Test
    void rejectsInvalidPassword() {
        User user = activeUser(UserStatus.ACTIVE);
        when(userRepository.findByEmail(any())).thenReturn(java.util.Optional.of(user));
        when(tenantAccessRepository.isActive(user.tenantId())).thenReturn(true);
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> useCase.authenticate(new AuthenticateUserCommand("jane@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private User activeUser(UserStatus status) {
        return User.reconstitute(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "jane@example.com",
                "hash",
                "Jane",
                "Doe",
                status,
                Set.of(Role.EMPLOYEE),
                NOW,
                NOW);
    }
}
