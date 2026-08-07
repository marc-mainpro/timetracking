package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.domain.InvalidVerificationTokenException;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import com.tfp.timetracking.tenant.domain.VerificationTokenGenerator;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T53-05: consume el token de verificación de correo. La solicitud pasa a
 * {@code PENDING_REVIEW}; sigue sin existir ningún tenant.
 *
 * <p>La búsqueda es <b>por hash del token</b>: el token en claro nunca llega al
 * repositorio ni a la base de datos. Un token caducado se marca además como
 * {@code EXPIRED} para que la solicitud no quede colgada eternamente en
 * «pendiente de verificación».
 */
@Service
public class VerifyTenantRegistrationEmailUseCase {

    private final TenantRegistrationRepository registrationRepository;
    private final VerificationTokenGenerator tokenGenerator;
    private final DomainEventPublisher domainEventPublisher;
    private final RegistrationAuditTrail auditTrail;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public VerifyTenantRegistrationEmailUseCase(
            TenantRegistrationRepository registrationRepository,
            VerificationTokenGenerator tokenGenerator,
            DomainEventPublisher domainEventPublisher,
            RegistrationAuditTrail auditTrail,
            Clock clock,
            IdGenerator idGenerator) {
        this.registrationRepository = registrationRepository;
        this.tokenGenerator = tokenGenerator;
        this.domainEventPublisher = domainEventPublisher;
        this.auditTrail = auditTrail;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public void verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidVerificationTokenException();
        }
        TenantRegistration registration = registrationRepository
                .findByVerificationTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(InvalidVerificationTokenException::new);

        if (registration.isVerificationExpiredAt(clock.now())) {
            registration.expire(clock);
            registrationRepository.save(registration);
            auditTrail.record("TENANT_REGISTRATION_EXPIRED", registration.id(), Map.of());
            throw new InvalidVerificationTokenException();
        }

        registration.verifyEmail(rawToken, tokenGenerator, clock, idGenerator);
        TenantRegistration saved = registrationRepository.save(registration);
        domainEventPublisher.publish(registration.pullDomainEvents());
        auditTrail.record("TENANT_REGISTRATION_EMAIL_VERIFIED", saved.id(), Map.of("status", saved.status().name()));
    }
}
