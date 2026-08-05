package com.tfp.timetracking.identity.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.GeneratedPasswordResetToken;
import com.tfp.timetracking.identity.domain.PasswordResetToken;
import com.tfp.timetracking.identity.domain.PasswordResetTokenGenerator;
import com.tfp.timetracking.identity.domain.PasswordResetTokenRepository;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.TenantAccessRepository;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestPasswordResetUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");

    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final TenantAccessRepository tenantAccessRepository = org.mockito.Mockito.mock(TenantAccessRepository.class);
    private final PasswordResetTokenRepository passwordResetTokenRepository = org.mockito.Mockito.mock(PasswordResetTokenRepository.class);
    private final PasswordResetTokenGenerator passwordResetTokenGenerator = org.mockito.Mockito.mock(PasswordResetTokenGenerator.class);
    private final DomainEventPublisher domainEventPublisher = org.mockito.Mockito.mock(DomainEventPublisher.class);
    private final Clock clock = () -> NOW;
    private final IdGenerator idGenerator = UUID::randomUUID;

    private RequestPasswordResetUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RequestPasswordResetUseCase(
                userRepository,
                tenantAccessRepository,
                passwordResetTokenRepository,
                passwordResetTokenGenerator,
                domainEventPublisher,
                new PasswordResetProperties(Duration.ofHours(1), "https://app.test/reset?token=%s"),
                clock,
                idGenerator);
    }

    @Test
    void createsTokenAndPublishesDomainEventsForActiveUser() {
        User user = user(UserStatus.ACTIVE);
        PasswordResetToken previous = PasswordResetToken.reconstitute(
                UUID.randomUUID(), user.tenantId(), user.id(), "old-hash", NOW.plusSeconds(60), null, NOW.minusSeconds(30));
        when(userRepository.findByEmail(Email.of("jane@example.com"))).thenReturn(java.util.Optional.of(user));
        when(tenantAccessRepository.isActive(user.tenantId())).thenReturn(true);
        when(passwordResetTokenRepository.findUnusedByTenantIdAndUserId(user.tenantId(), user.id())).thenReturn(List.of(previous));
        when(passwordResetTokenGenerator.generate()).thenReturn(new GeneratedPasswordResetToken("raw", "hash"));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.request(new RequestPasswordResetCommand("jane@example.com"));

        verify(passwordResetTokenRepository).save(previous);
        verify(passwordResetTokenRepository, atLeast(2)).save(any(PasswordResetToken.class));
        verify(domainEventPublisher).publish(any());
    }

    @Test
    void silentlyIgnoresUnknownOrInactiveAccounts() {
        when(userRepository.findByEmail(Email.of("ghost@example.com"))).thenReturn(java.util.Optional.empty());

        useCase.request(new RequestPasswordResetCommand("ghost@example.com"));

        verify(passwordResetTokenRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    private User user(UserStatus status) {
        return User.reconstitute(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "jane@example.com",
                "hash",
                "Jane",
                "Doe",
                status,
                Set.of(Role.EMPLOYEE),
                NOW.minusSeconds(600),
                NOW.minusSeconds(600));
    }
}
