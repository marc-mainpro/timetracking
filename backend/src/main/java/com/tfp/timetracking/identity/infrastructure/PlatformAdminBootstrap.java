package com.tfp.timetracking.identity.infrastructure;

import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aprovisiona de forma idempotente el usuario {@code PLATFORM_ADMIN} inicial
 * (T50-04) en el tenant de sistema ({@link PlatformTenant#ID}, creado por la
 * migración V11) a partir de configuración/entorno.
 *
 * <p>La contraseña es un secreto y nunca vive en el repositorio: si
 * {@code platform.admin.email} o {@code platform.admin.password} no están
 * definidos, el bootstrap no hace nada (p.ej. en tests o entornos donde el
 * PLATFORM_ADMIN ya existe). Si el email ya está registrado, tampoco se
 * recrea. Así el rol se aprovisiona de forma controlada, nunca desde la
 * administración de un tenant ni por registro público.
 */
@Component
public class PlatformAdminBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final String email;
    private final String password;

    public PlatformAdminBootstrap(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            Clock clock,
            IdGenerator idGenerator,
            @Value("${platform.admin.email:}") String email,
            @Value("${platform.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        Email platformEmail = Email.of(email);
        if (userRepository.existsByEmail(platformEmail)) {
            return;
        }
        User admin = User.create(
                PlatformTenant.ID,
                platformEmail.value(),
                passwordHasher.hash(password),
                "Platform",
                "Admin",
                EnumSet.of(Role.PLATFORM_ADMIN),
                clock,
                idGenerator);
        admin.pullDomainEvents(); // aprovisionamiento de infraestructura: no se publica como hecho de negocio
        userRepository.save(admin);
        logger.info("Usuario PLATFORM_ADMIN aprovisionado para {}", platformEmail.value());
    }
}
