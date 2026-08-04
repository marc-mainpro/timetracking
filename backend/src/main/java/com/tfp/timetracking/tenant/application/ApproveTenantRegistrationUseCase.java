package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.EmailAlreadyInUseException;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import com.tfp.timetracking.tenant.domain.TenantRegistrationStatus;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T53-03: aprueba una solicitud verificada y crea, <b>en una sola
 * transacción</b>, el tenant y su primer {@code TENANT_ADMIN}.
 *
 * <p>El tenant nace en {@code PENDING} vía {@link Tenant#requestRegistration}, no
 * en {@code ACTIVE}: aprobar la solicitud significa «esta organización es
 * legítima», no «esta organización ya puede operar». La activación sigue siendo
 * una decisión aparte del {@code PLATFORM_ADMIN}
 * ({@code POST /api/v1/platform/tenants/{id}/activate}, RF-TEN-005).
 *
 * <p><b>Idempotencia.</b> Aprobar dos veces la misma solicitud no crea dos
 * tenants: la segunda llamada encuentra la solicitud en {@code CONSUMED} y
 * devuelve el tenant que ya se creó. Que la solicitud pase por {@code APPROVED}
 * y {@code CONSUMED} dentro de la misma transacción hace que el registro del
 * estado y la creación del tenant sean atómicos entre sí: no existe un instante
 * observable con la solicitud aprobada y sin tenant.
 */
@Service
public class ApproveTenantRegistrationUseCase {

    private final TenantRegistrationRepository registrationRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditRecorder auditRecorder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public ApproveTenantRegistrationUseCase(
            TenantRegistrationRepository registrationRepository,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            DomainEventPublisher domainEventPublisher,
            AuditRecorder auditRecorder,
            Clock clock,
            IdGenerator idGenerator) {
        this.registrationRepository = registrationRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public TenantRegistration approve(UUID registrationId) {
        TenantRegistration registration = registrationRepository
                .findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de alta no encontrada"));

        if (registration.status() == TenantRegistrationStatus.CONSUMED) {
            return registration;
        }

        registration.approve(clock);

        Email ownerEmail = Email.of(registration.email());
        if (userRepository.existsByEmail(ownerEmail)) {
            // El correo se registró por otra vía entre la solicitud y la
            // aprobación. No se puede crear el propietario: se aborta la
            // aprobación en vez de dejar un tenant sin administrador.
            throw new EmailAlreadyInUseException(ownerEmail.value());
        }

        Tenant tenant = Tenant.requestRegistration(registration.companyName(), registration.timezone(), clock, idGenerator);
        User owner = User.create(
                tenant.id(),
                registration.email(),
                registration.ownerPasswordHash(),
                registration.ownerFirstName(),
                registration.ownerLastName(),
                Set.of(Role.TENANT_ADMIN),
                clock,
                idGenerator);

        tenantRepository.save(tenant);
        userRepository.save(owner);
        registration.markConsumed(tenant.id(), owner.id(), clock, idGenerator);
        TenantRegistration saved = registrationRepository.save(registration);

        List<Object> events = new ArrayList<>();
        events.addAll(tenant.pullDomainEvents());
        events.addAll(owner.pullDomainEvents());
        events.addAll(registration.pullDomainEvents());
        domainEventPublisher.publish(events);

        auditRecorder.record(
                "TENANT_REGISTRATION_APPROVED",
                "TenantRegistration",
                saved.id(),
                Map.of(
                        "tenantId", tenant.id().toString(),
                        "ownerUserId", owner.id().toString(),
                        "tenantStatus", tenant.status().name()));
        return saved;
    }
}
