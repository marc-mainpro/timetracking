package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.audit.application.AuditRecorder;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creación de un tenant por un {@code PLATFORM_ADMIN} desde la administración
 * de plataforma (RF-TEN-003 «creación manual», T50-05). Reutiliza
 * {@link RegisterTenantUseCase} para crear de forma transaccional el tenant y
 * su primer {@code TENANT_ADMIN}, y añade auditoría de plataforma dentro de la
 * misma transacción (el actor es el {@code PLATFORM_ADMIN} del contexto).
 *
 * <p>A diferencia de {@code RegisterTenantUseCase} (que también usa el arranque
 * de tests sin contexto de seguridad), este caso de uso siempre se ejecuta bajo
 * un principal autenticado, por lo que puede registrar auditoría.
 */
@Service
public class CreateTenantUseCase {

    private final RegisterTenantUseCase registerTenantUseCase;
    private final AuditRecorder auditRecorder;

    public CreateTenantUseCase(RegisterTenantUseCase registerTenantUseCase, AuditRecorder auditRecorder) {
        this.registerTenantUseCase = registerTenantUseCase;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public RegisterTenantResult create(RegisterTenantCommand command) {
        RegisterTenantResult result = registerTenantUseCase.register(command);
        auditRecorder.record(
                "TENANT_CREATED",
                "Tenant",
                result.tenantId(),
                Map.of("name", command.tenantName(), "adminUserId", result.adminUserId().toString()));
        return result;
    }
}
