package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.InvalidPasswordResetTokenException;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.PasswordResetToken;
import com.tfp.timetracking.identity.domain.PasswordResetTokenGenerator;
import com.tfp.timetracking.identity.domain.PasswordResetTokenRepository;
import com.tfp.timetracking.identity.domain.RefreshToken;
import com.tfp.timetracking.identity.domain.RefreshTokenRepository;
import com.tfp.timetracking.identity.domain.Session;
import com.tfp.timetracking.identity.domain.SessionRepository;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResetPasswordUseCase {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenGenerator passwordResetTokenGenerator;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRepository sessionRepository;
    private final AccountLockoutService accountLockoutService;
    private final Clock clock;

    public ResetPasswordUseCase(
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordResetTokenGenerator passwordResetTokenGenerator,
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            RefreshTokenRepository refreshTokenRepository,
            SessionRepository sessionRepository,
            AccountLockoutService accountLockoutService,
            Clock clock) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetTokenGenerator = passwordResetTokenGenerator;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionRepository = sessionRepository;
        this.accountLockoutService = accountLockoutService;
        this.clock = clock;
    }

    @Transactional
    public void reset(ResetPasswordCommand command) {
        if (command.token() == null || command.token().isBlank()) {
            throw new InvalidPasswordResetTokenException();
        }
        Instant now = clock.now();
        PasswordResetToken passwordResetToken = passwordResetTokenRepository
                .findByTokenHashForUpdate(passwordResetTokenGenerator.hash(command.token()))
                .orElseThrow(InvalidPasswordResetTokenException::new);
        passwordResetToken.consume(now);
        User user = userRepository
                .findById(passwordResetToken.tenantId(), passwordResetToken.userId())
                .orElseThrow(InvalidPasswordResetTokenException::new);
        user.changePassword(passwordHasher.hash(command.newPassword()), clock);
        userRepository.save(user);
        passwordResetTokenRepository.save(passwordResetToken);
        for (Session session : sessionRepository.findByUserId(user.id())) {
            if (!session.isRevoked()) {
                session.revoke(now);
                sessionRepository.save(session);
            }
        }
        for (RefreshToken refreshToken : refreshTokenRepository.findByUserId(user.id())) {
            if (!refreshToken.isRevoked()) {
                refreshToken.revoke(now);
                refreshTokenRepository.save(refreshToken);
            }
        }
        accountLockoutService.registerSuccessfulAttempt(user);
    }
}
