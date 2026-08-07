package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.GeneratedPasswordResetToken;
import com.tfp.timetracking.identity.domain.PasswordResetToken;
import com.tfp.timetracking.identity.domain.PasswordResetTokenGenerator;
import com.tfp.timetracking.identity.domain.PasswordResetTokenRepository;
import com.tfp.timetracking.identity.domain.TenantAccessRepository;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestPasswordResetUseCase {

    private final UserRepository userRepository;
    private final TenantAccessRepository tenantAccessRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenGenerator passwordResetTokenGenerator;
    private final DomainEventPublisher domainEventPublisher;
    private final PasswordResetProperties properties;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public RequestPasswordResetUseCase(
            UserRepository userRepository,
            TenantAccessRepository tenantAccessRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordResetTokenGenerator passwordResetTokenGenerator,
            DomainEventPublisher domainEventPublisher,
            PasswordResetProperties properties,
            Clock clock,
            IdGenerator idGenerator) {
        this.userRepository = userRepository;
        this.tenantAccessRepository = tenantAccessRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetTokenGenerator = passwordResetTokenGenerator;
        this.domainEventPublisher = domainEventPublisher;
        this.properties = properties;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public void request(RequestPasswordResetCommand command) {
        if (command.email() == null || command.email().isBlank()) {
            return;
        }
        User user = userRepository.findByEmail(com.tfp.timetracking.identity.domain.Email.of(command.email())).orElse(null);
        if (user == null || !user.isActive() || !tenantAccessRepository.isActive(user.tenantId())) {
            return;
        }

        for (PasswordResetToken token : passwordResetTokenRepository.findUnusedByTenantIdAndUserId(user.tenantId(), user.id())) {
            token.invalidate(clock.now());
            passwordResetTokenRepository.save(token);
        }

        GeneratedPasswordResetToken generatedToken = passwordResetTokenGenerator.generate();
        PasswordResetToken token = PasswordResetToken.issue(user, generatedToken, properties.tokenTtl(), clock, idGenerator);
        passwordResetTokenRepository.save(token);
        domainEventPublisher.publish(token.pullDomainEvents());
    }
}
