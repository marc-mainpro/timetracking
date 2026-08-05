package com.tfp.timetracking.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.identity.domain.InvalidPasswordResetTokenException;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.PasswordResetToken;
import com.tfp.timetracking.identity.domain.PasswordResetTokenGenerator;
import com.tfp.timetracking.identity.domain.PasswordResetTokenRepository;
import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.shared.domain.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResetPasswordUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");

    private final PasswordResetTokenRepository passwordResetTokenRepository = org.mockito.Mockito.mock(PasswordResetTokenRepository.class);
    private final PasswordResetTokenGenerator passwordResetTokenGenerator = org.mockito.Mockito.mock(PasswordResetTokenGenerator.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final PasswordHasher passwordHasher = org.mockito.Mockito.mock(PasswordHasher.class);
    private final RefreshTokenRepository refreshTokenRepository = org.mockito.Mockito.mock(RefreshTokenRepository.class);
    private final AccountLockoutService accountLockoutService = org.mockito.Mockito.mock(AccountLockoutService.class);
    private final Clock clock = () -> NOW;

    private ResetPasswordUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResetPasswordUseCase(
                passwordResetTokenRepository,
                passwordResetTokenGenerator,
                userRepository,
                passwordHasher,
                refreshTokenRepository,
                accountLockoutService,
                clock);
    }

    @Test
    void resetsPasswordConsumesTokenAndRevokesRefreshTokens() {
        User user = user();
        PasswordResetToken token = PasswordResetToken.reconstitute(
                UUID.randomUUID(), user.tenantId(), user.id(), "token-hash", NOW.plusSeconds(60), null, NOW.minusSeconds(30));
        RefreshToken refreshToken = RefreshToken.reconstitute(
                UUID.randomUUID(), user.id(), "refresh-hash", NOW.plusSeconds(3600), null, null, NOW.minusSeconds(300));
        when(passwordResetTokenGenerator.hash("raw-token")).thenReturn("token-hash");
        when(passwordResetTokenRepository.findByTokenHashForUpdate("token-hash")).thenReturn(java.util.Optional.of(token));
        when(userRepository.findById(user.tenantId(), user.id())).thenReturn(java.util.Optional.of(user));
        when(passwordHasher.hash("new-password-123")).thenReturn("new-password-hash");
        when(refreshTokenRepository.findByUserId(user.id())).thenReturn(List.of(refreshToken));

        useCase.reset(new ResetPasswordCommand("raw-token", "new-password-123"));

        assertThat(token.usedAt()).isEqualTo(NOW);
        assertThat(user.passwordHash()).isEqualTo("new-password-hash");
        assertThat(refreshToken.isRevoked()).isTrue();
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
        verify(refreshTokenRepository).save(refreshToken);
        verify(accountLockoutService).registerSuccessfulAttempt(user);
    }

    @Test
    void rejectsUnknownToken() {
        when(passwordResetTokenGenerator.hash("missing")).thenReturn("missing-hash");
        when(passwordResetTokenRepository.findByTokenHashForUpdate("missing-hash")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> useCase.reset(new ResetPasswordCommand("missing", "new-password-123")))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    private User user() {
        return User.reconstitute(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "jane@example.com",
                "hash",
                "Jane",
                "Doe",
                UserStatus.ACTIVE,
                Set.of(Role.EMPLOYEE),
                NOW.minusSeconds(600),
                NOW.minusSeconds(600));
    }
}
