package com.tfp.timetracking.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import com.tfp.timetracking.tenant.domain.TenantStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetTenantUseCaseTest {

    private final TenantRepository tenantRepository = org.mockito.Mockito.mock(TenantRepository.class);
    private final GetTenantUseCase useCase = new GetTenantUseCase(tenantRepository);

    @Test
    void returnsTenantById() {
        Instant now = Instant.parse("2026-07-24T10:00:00Z");
        Tenant tenant =
                Tenant.reconstitute(UUID.randomUUID(), "Acme", TenantStatus.ACTIVE, "UTC", now, now, now, null, null, null);
        when(tenantRepository.findById(tenant.id())).thenReturn(Optional.of(tenant));

        assertThat(useCase.get(tenant.id())).isSameAs(tenant);
    }

    @Test
    void failsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.findById(id)).thenReturn(Optional.empty());
        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> useCase.get(id));
    }

    @Test
    void refusesSystemTenant() {
        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> useCase.get(PlatformTenant.ID));
        verify(tenantRepository, never()).findById(PlatformTenant.ID);
    }
}
