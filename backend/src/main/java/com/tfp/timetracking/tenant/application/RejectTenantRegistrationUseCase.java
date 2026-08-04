package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T53-03: rechaza una solicitud de alta verificada, con motivo obligatorio
 * (diseño §7.5). No crea ni toca ningún tenant.
 */
@Service
public class RejectTenantRegistrationUseCase {

    private final TenantRegistrationRepository registrationRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditRecorder auditRecorder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public RejectTenantRegistrationUseCase(
            TenantRegistrationRepository registrationRepository,
            DomainEventPublisher domainEventPublisher,
            AuditRecorder auditRecorder,
            Clock clock,
            IdGenerator idGenerator) {
        this.registrationRepository = registrationRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public TenantRegistration reject(UUID registrationId, String reason) {
        TenantRegistration registration = registrationRepository
                .findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de alta no encontrada"));

        registration.reject(reason, clock, idGenerator);
        TenantRegistration saved = registrationRepository.save(registration);
        domainEventPublisher.publish(registration.pullDomainEvents());
        auditRecorder.record(
                "TENANT_REGISTRATION_REJECTED",
                "TenantRegistration",
                saved.id(),
                Map.of("reason", saved.decisionReason()));
        return saved;
    }
}
