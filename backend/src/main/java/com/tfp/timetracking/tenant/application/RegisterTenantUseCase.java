package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.EmailAlreadyInUseException;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso T203: registra una nueva organizacion (crea {@link Tenant} +
 * primer usuario {@code TENANT_ADMIN}) de forma transaccional.
 *
 * <p>La transaccionalidad se aplica aqui, en {@code application}, mediante
 * {@code @Transactional} de Spring: el dominio ({@code Tenant}, {@code User})
 * no depende de Spring (CONTEXT-GLOBAL §4). Esta anotacion no viola las
 * reglas de ArchUnit porque estas solo prohiben que {@code ..domain..}
 * dependa de Spring; {@code application} puede usarlo para configurar
 * infraestructura transversal (ver skill {@code create-use-case}).
 */
@Service
public class RegisterTenantUseCase {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public RegisterTenantUseCase(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            DomainEventPublisher domainEventPublisher,
            Clock clock,
            IdGenerator idGenerator) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public RegisterTenantResult register(RegisterTenantCommand command) {
        Tenant tenant = Tenant.register(command.tenantName(), command.timezone(), clock, idGenerator);

        Email adminEmail = Email.of(command.adminEmail());
        if (userRepository.existsByTenantIdAndEmail(tenant.id(), adminEmail)) {
            throw new EmailAlreadyInUseException(adminEmail.value());
        }

        String passwordHash = passwordHasher.hash(command.adminPassword());
        User admin = User.create(
                tenant.id(),
                command.adminEmail(),
                passwordHash,
                command.adminFirstName(),
                command.adminLastName(),
                Set.of(Role.TENANT_ADMIN),
                clock,
                idGenerator);

        tenantRepository.save(tenant);
        userRepository.save(admin);

        List<Object> events = new ArrayList<>();
        events.addAll(tenant.pullDomainEvents());
        events.addAll(admin.pullDomainEvents());
        domainEventPublisher.publish(events);

        return new RegisterTenantResult(tenant.id(), admin.id());
    }
}
