package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.EmailAlreadyInUseException;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateEmployeeUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TenantContext tenantContext;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditRecorder auditRecorder;

    public CreateEmployeeUseCase(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            TenantContext tenantContext,
            Clock clock,
            IdGenerator idGenerator,
            DomainEventPublisher domainEventPublisher,
            AuditRecorder auditRecorder) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.domainEventPublisher = domainEventPublisher;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public User create(CreateEmployeeCommand command) {
        Email email = Email.of(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException(email.value());
        }
        User user = User.create(
                tenantContext.currentTenantId(),
                command.email(),
                passwordHasher.hash(command.password()),
                command.firstName(),
                command.lastName(),
                command.roles().stream().map(Role::valueOf).collect(Collectors.toSet()),
                clock,
                idGenerator);
        User saved = userRepository.save(user);
        domainEventPublisher.publish(user.pullDomainEvents());
        auditRecorder.record(
                "EMPLOYEE_CREATED",
                "User",
                saved.id(),
                java.util.Map.of("email", saved.email().value(), "roles", saved.roles().stream().map(Enum::name).toList()));
        return saved;
    }
}
