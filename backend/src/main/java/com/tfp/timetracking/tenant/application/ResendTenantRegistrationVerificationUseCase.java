package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.DomainException;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import com.tfp.timetracking.tenant.domain.VerificationTokenGenerator;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T53-05: reenvía el correo de verificación de una solicitud pendiente.
 *
 * <p>Igual que el alta, es <b>anti-enumeración</b> (RF-REG-005): no devuelve
 * nada y no lanza excepciones de negocio. Correo inexistente, solicitud ya
 * verificada y reenvíos agotados son indistinguibles desde fuera. El límite de
 * reenvíos vive en el agregado, no aquí.
 */
@Service
public class ResendTenantRegistrationVerificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResendTenantRegistrationVerificationUseCase.class);

    private final TenantRegistrationRepository registrationRepository;
    private final VerificationTokenGenerator tokenGenerator;
    private final DomainEventPublisher domainEventPublisher;
    private final RegistrationAuditTrail auditTrail;
    private final RegistrationProperties properties;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public ResendTenantRegistrationVerificationUseCase(
            TenantRegistrationRepository registrationRepository,
            VerificationTokenGenerator tokenGenerator,
            DomainEventPublisher domainEventPublisher,
            RegistrationAuditTrail auditTrail,
            RegistrationProperties properties,
            Clock clock,
            IdGenerator idGenerator) {
        this.registrationRepository = registrationRepository;
        this.tokenGenerator = tokenGenerator;
        this.domainEventPublisher = domainEventPublisher;
        this.auditTrail = auditTrail;
        this.properties = properties;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public void resend(String rawEmail) {
        String email;
        try {
            email = Email.of(rawEmail).value();
        } catch (IllegalArgumentException malformed) {
            // Ni siquiera el formato inválido debe distinguirse aquí: el
            // endpoint ya valida el formato con Bean Validation; llegar hasta
            // este punto con un correo raro no justifica una respuesta distinta.
            log.info("registration.resend-ignored-malformed-email");
            return;
        }

        Optional<TenantRegistration> found = registrationRepository.findOpenByEmail(email);
        if (found.isEmpty()) {
            log.info("registration.resend-ignored-unknown-email");
            return;
        }

        TenantRegistration registration = found.get();
        try {
            registration.resendVerification(
                    properties.verification().maxResends(),
                    properties.verification().tokenTtl(),
                    clock,
                    idGenerator,
                    tokenGenerator);
        } catch (DomainException notResendable) {
            log.info("registration.resend-refused errorCode={}", notResendable.errorCode());
            return;
        }

        registrationRepository.save(registration);
        domainEventPublisher.publish(registration.pullDomainEvents());
        auditTrail.record(
                "TENANT_REGISTRATION_VERIFICATION_RESENT",
                registration.id(),
                Map.of("trigger", "explicit", "resendCount", registration.resendCount()));
    }
}
