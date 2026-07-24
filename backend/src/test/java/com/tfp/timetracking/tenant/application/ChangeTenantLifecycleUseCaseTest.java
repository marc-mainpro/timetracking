package com.tfp.timetracking.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.tenant.domain.IllegalTenantTransitionException;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import com.tfp.timetracking.tenant.domain.TenantStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Pruebas unitarias (Mockito) de {@link ChangeTenantLifecycleUseCase} (T50-05, T130-03). */
class ChangeTenantLifecycleUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");

    private final TenantRepository tenantRepository = org.mockito.Mockito.mock(TenantRepository.class);
    private final DomainEventPublisher domainEventPublisher = org.mockito.Mockito.mock(DomainEventPublisher.class);
    private final AuditRecorder auditRecorder = org.mockito.Mockito.mock(AuditRecorder.class);
    private final Clock clock = () -> NOW;
    private final IdGenerator idGenerator = UUID::randomUUID;

    private ChangeTenantLifecycleUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChangeTenantLifecycleUseCase(
                tenantRepository, domainEventPublisher, auditRecorder, clock, idGenerator);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Tenant tenantWith(TenantStatus status) {
        return Tenant.reconstitute(
                UUID.randomUUID(), "Acme", status, "Europe/Madrid", NOW, NOW, NOW, null, null, null);
    }

    @Test
    void activatesPendingTenantAndAuditsTransition() {
        Tenant pending = tenantWith(TenantStatus.PENDING);
        when(tenantRepository.findById(pending.id())).thenReturn(Optional.of(pending));

        Tenant result = useCase.activate(pending.id());

        assertThat(result.status()).isEqualTo(TenantStatus.ACTIVE);
        verify(domainEventPublisher).publish(any());
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditRecorder).record(eq("TENANT_ACTIVATED"), eq("Tenant"), eq(pending.id()), metadata.capture());
        assertThat(metadata.getValue())
                .containsEntry("previousStatus", "PENDING")
                .containsEntry("newStatus", "ACTIVE");
    }

    @Test
    void suspendsActiveTenantWithReasonInAudit() {
        Tenant active = tenantWith(TenantStatus.ACTIVE);
        when(tenantRepository.findById(active.id())).thenReturn(Optional.of(active));

        useCase.suspend(active.id(), "Impago");

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditRecorder).record(eq("TENANT_SUSPENDED"), eq("Tenant"), eq(active.id()), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("reason", "Impago").containsEntry("newStatus", "SUSPENDED");
    }

    @Test
    void reactivatesSuspendedTenant() {
        Tenant suspended = Tenant.reconstitute(
                UUID.randomUUID(), "Acme", TenantStatus.SUSPENDED, "Europe/Madrid", NOW, NOW, NOW, NOW, null, "x");
        when(tenantRepository.findById(suspended.id())).thenReturn(Optional.of(suspended));

        assertThat(useCase.reactivate(suspended.id()).status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void archivesActiveTenant() {
        Tenant active = tenantWith(TenantStatus.ACTIVE);
        when(tenantRepository.findById(active.id())).thenReturn(Optional.of(active));

        assertThat(useCase.archive(active.id(), null).status()).isEqualTo(TenantStatus.ARCHIVED);
    }

    @Test
    void rejectsInvalidTransitionWithoutAudit() {
        Tenant active = tenantWith(TenantStatus.ACTIVE);
        when(tenantRepository.findById(active.id())).thenReturn(Optional.of(active));

        assertThatExceptionOfType(IllegalTenantTransitionException.class)
                .isThrownBy(() -> useCase.activate(active.id()));
        verify(auditRecorder, never()).record(any(), any(), any(), any());
    }

    @Test
    void failsWhenTenantMissing() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> useCase.suspend(id, "x"));
    }

    @Test
    void refusesToManageSystemTenant() {
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> useCase.suspend(PlatformTenant.ID, "x"));
        verify(tenantRepository, never()).findById(any(UUID.class));
    }
}
