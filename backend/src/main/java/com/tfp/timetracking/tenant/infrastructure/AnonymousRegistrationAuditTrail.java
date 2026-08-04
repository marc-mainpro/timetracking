package com.tfp.timetracking.tenant.infrastructure;

import com.tfp.timetracking.audit.domain.AuditEvent;
import com.tfp.timetracking.audit.domain.AuditEventRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.shared.infrastructure.security.CorrelationIdFilter;
import com.tfp.timetracking.tenant.application.RegistrationAuditTrail;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link RegistrationAuditTrail} (RF-REG-006): escribe el
 * hecho en la misma tabla de auditoría que el resto del sistema, con el tenant
 * de plataforma y sin actor, porque eso es exactamente lo que es una acción del
 * alta pública: la acción de un desconocido sobre la plataforma, no la de un
 * usuario dentro de un tenant.
 */
@Component
public class AnonymousRegistrationAuditTrail implements RegistrationAuditTrail {

    private final AuditEventRepository auditEventRepository;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public AnonymousRegistrationAuditTrail(
            AuditEventRepository auditEventRepository, Clock clock, IdGenerator idGenerator) {
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public void record(String action, UUID registrationId, Map<String, Object> metadata) {
        auditEventRepository.save(new AuditEvent(
                idGenerator.newId(),
                PlatformTenant.ID,
                null,
                action,
                "TenantRegistration",
                registrationId,
                currentCorrelationId(),
                Map.copyOf(metadata),
                clock.now()));
    }

    private static UUID currentCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return correlationId != null ? UUID.fromString(correlationId) : UUID.randomUUID();
    }
}
