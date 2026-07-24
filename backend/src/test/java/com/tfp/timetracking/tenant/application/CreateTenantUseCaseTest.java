package com.tfp.timetracking.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.audit.application.AuditRecorder;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateTenantUseCaseTest {

    private final RegisterTenantUseCase registerTenantUseCase = org.mockito.Mockito.mock(RegisterTenantUseCase.class);
    private final AuditRecorder auditRecorder = org.mockito.Mockito.mock(AuditRecorder.class);
    private final CreateTenantUseCase useCase = new CreateTenantUseCase(registerTenantUseCase, auditRecorder);

    @Test
    void createsTenantViaRegisterAndRecordsPlatformAudit() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        RegisterTenantCommand command =
                new RegisterTenantCommand("Acme", "Europe/Madrid", "admin@acme.test", "supersecretpwd", "Jane", "Doe");
        when(registerTenantUseCase.register(command)).thenReturn(new RegisterTenantResult(tenantId, adminId));

        RegisterTenantResult result = useCase.create(command);

        assertThat(result.tenantId()).isEqualTo(tenantId);
        verify(registerTenantUseCase).register(command);
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditRecorder).record(eq("TENANT_CREATED"), eq("Tenant"), eq(tenantId), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("name", "Acme");
    }
}
