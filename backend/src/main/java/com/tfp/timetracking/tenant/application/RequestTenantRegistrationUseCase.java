package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.DomainException;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.domain.IpHasher;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import com.tfp.timetracking.tenant.domain.VerificationTokenGenerator;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T53-03/T53-05: registra una <b>solicitud</b> de alta pública. No crea ningún
 * tenant ni ningún usuario; eso ocurre solo si un {@code PLATFORM_ADMIN}
 * aprueba la solicitud ({@link ApproveTenantRegistrationUseCase}).
 *
 * <p><b>Anti-enumeración (RF-REG-005).</b> El método no devuelve nada y no lanza
 * ninguna excepción de negocio: correo ya registrado, solicitud duplicada y
 * límite de abuso alcanzado producen exactamente el mismo resultado observable
 * que una solicitud nueva y válida. Un atacante que pruebe correos no puede
 * distinguir ninguno de esos casos, ni por el cuerpo de la respuesta ni por su
 * código de estado. Las únicas diferencias posibles son de tiempo, y no
 * dependen de si el correo existe: los tres caminos hacen el mismo trabajo caro
 * (hash de contraseña) antes de decidir.
 */
@Service
public class RequestTenantRegistrationUseCase {

    private static final Logger log = LoggerFactory.getLogger(RequestTenantRegistrationUseCase.class);

    private final TenantRegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final VerificationTokenGenerator tokenGenerator;
    private final IpHasher ipHasher;
    private final DomainEventPublisher domainEventPublisher;
    private final RegistrationAuditTrail auditTrail;
    private final RegistrationProperties properties;
    private final Clock clock;
    private final IdGenerator idGenerator;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public RequestTenantRegistrationUseCase(
            TenantRegistrationRepository registrationRepository,
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            VerificationTokenGenerator tokenGenerator,
            IpHasher ipHasher,
            DomainEventPublisher domainEventPublisher,
            RegistrationAuditTrail auditTrail,
            RegistrationProperties properties,
            Clock clock,
            IdGenerator idGenerator) {
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenGenerator = tokenGenerator;
        this.ipHasher = ipHasher;
        this.domainEventPublisher = domainEventPublisher;
        this.auditTrail = auditTrail;
        this.properties = properties;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public void request(RequestTenantRegistrationCommand command) {
        // Valida formato ANTES de cualquier consulta: un email malformado es un
        // 400 legítimo y no revela nada sobre qué correos existen.
        String email = Email.of(command.email()).value();
        String passwordHash = passwordHasher.hash(command.password());
        String ipHash = ipHasher.hash(command.clientIp());
        Instant now = clock.now();
        Instant windowStart = now.minus(properties.throttle().window());

        if (isThrottled(email, ipHash, windowStart)) {
            log.warn("registration.throttled ipHash={} window={}", ipHash, properties.throttle().window());
            return;
        }
        if (userRepository.existsByEmail(Email.of(email))) {
            // RF-REG-005: no se crea nada y no se avisa. El titular legítimo de
            // ese correo ya tiene cuenta y sabe cómo entrar.
            log.info("registration.ignored-existing-account");
            return;
        }

        Optional<TenantRegistration> open = registrationRepository.findOpenByEmail(email);
        if (open.isPresent()) {
            resendOrIgnore(open.get());
            return;
        }

        TenantRegistration registration = TenantRegistration.request(
                command.companyName(),
                command.ownerFirstName(),
                command.ownerLastName(),
                email,
                passwordHash,
                command.timezone(),
                command.source(),
                ipHash,
                properties.verification().tokenTtl(),
                clock,
                idGenerator,
                tokenGenerator);

        TenantRegistration saved = registrationRepository.save(registration);
        domainEventPublisher.publish(registration.pullDomainEvents());
        auditTrail.record(
                "TENANT_REGISTRATION_REQUESTED",
                saved.id(),
                Map.of("companyName", saved.companyName(), "source", saved.source()));
    }

    private boolean isThrottled(String email, String ipHash, Instant windowStart) {
        return registrationRepository.countByIpHashSince(ipHash, windowStart) >= properties.throttle().maxPerIp()
                || registrationRepository.countByEmailSince(email, windowStart)
                        >= properties.throttle().maxPerEmail();
    }

    /**
     * Ya hay una solicitud viva para ese correo: en vez de crear un duplicado se
     * reenvía la verificación, que es lo que casi siempre quería quien vuelve a
     * enviar el formulario. Si la solicitud ya no admite reenvío (verificada, o
     * agotados los reenvíos) no ocurre nada visible.
     */
    private void resendOrIgnore(TenantRegistration registration) {
        try {
            registration.resendVerification(
                    properties.verification().maxResends(),
                    properties.verification().tokenTtl(),
                    clock,
                    idGenerator,
                    tokenGenerator);
        } catch (DomainException notResendable) {
            log.info("registration.duplicate-ignored errorCode={}", notResendable.errorCode());
            return;
        }
        registrationRepository.save(registration);
        domainEventPublisher.publish(registration.pullDomainEvents());
        auditTrail.record("TENANT_REGISTRATION_VERIFICATION_RESENT", registration.id(), Map.of("trigger", "duplicate"));
    }
}
