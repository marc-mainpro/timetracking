package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import com.tfp.timetracking.tenant.domain.TenantStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de administración del ciclo de vida de un tenant por
 * {@code PLATFORM_ADMIN} (RF-TEN-005..008, T50-05). Cada operación carga el
 * tenant, aplica la transición sobre el agregado (que valida el estado de
 * origen y emite el evento de dominio), persiste, publica los eventos de
 * integración vía Outbox y registra la acción en auditoría de plataforma
 * (RF-AUD-001, T130-03).
 */
@Service
public class ChangeTenantLifecycleUseCase {

    private final TenantRepository tenantRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditRecorder auditRecorder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public ChangeTenantLifecycleUseCase(
            TenantRepository tenantRepository,
            DomainEventPublisher domainEventPublisher,
            AuditRecorder auditRecorder,
            Clock clock,
            IdGenerator idGenerator) {
        this.tenantRepository = tenantRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public Tenant activate(UUID tenantId) {
        return apply(tenantId, "TENANT_ACTIVATED", null, tenant -> tenant.activate(clock, idGenerator));
    }

    @Transactional
    public Tenant suspend(UUID tenantId, String reason) {
        return apply(tenantId, "TENANT_SUSPENDED", reason, tenant -> tenant.suspend(reason, clock, idGenerator));
    }

    @Transactional
    public Tenant reactivate(UUID tenantId) {
        return apply(tenantId, "TENANT_REACTIVATED", null, tenant -> tenant.reactivate(clock, idGenerator));
    }

    @Transactional
    public Tenant archive(UUID tenantId, String reason) {
        return apply(tenantId, "TENANT_ARCHIVED", reason, tenant -> tenant.archive(reason, clock, idGenerator));
    }

    private Tenant apply(UUID tenantId, String action, String reason, Consumer<Tenant> transition) {
        if (PlatformTenant.ID.equals(tenantId)) {
            throw new ResourceNotFoundException("Tenant no encontrado");
        }
        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));
        TenantStatus previousStatus = tenant.status();
        transition.accept(tenant);
        Tenant saved = tenantRepository.save(tenant);
        domainEventPublisher.publish(tenant.pullDomainEvents());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("previousStatus", previousStatus.name());
        metadata.put("newStatus", saved.status().name());
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason.trim());
        }
        auditRecorder.record(action, "Tenant", saved.id(), metadata);
        return saved;
    }
}
