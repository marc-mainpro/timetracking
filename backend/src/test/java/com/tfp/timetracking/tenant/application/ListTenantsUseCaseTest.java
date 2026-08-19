package com.tfp.timetracking.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.shared.application.TenantUsageQuery;
import com.tfp.timetracking.shared.application.TenantUsageQuery.TenantUsage;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import com.tfp.timetracking.tenant.domain.TenantStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ListTenantsUseCaseTest {

    private static final Instant LAST_ACCESS = Instant.parse("2026-08-06T09:30:00Z");

    private final TenantRepository tenantRepository = org.mockito.Mockito.mock(TenantRepository.class);
    private final TenantUsageQuery tenantUsageQuery = org.mockito.Mockito.mock(TenantUsageQuery.class);
    private final ListTenantsUseCase useCase = new ListTenantsUseCase(tenantRepository, tenantUsageQuery);

    @Test
    void delegatesExcludingSystemTenantAndPropagatesFilter() {
        when(tenantRepository.findAllExcluding(PlatformTenant.ID, TenantStatus.SUSPENDED, "Ac", 0, 20))
                .thenReturn(new PagedResult<>(List.of(), 0, 20, 0, 0));
        when(tenantUsageQuery.findUsage(any())).thenReturn(Map.of());

        useCase.list(TenantStatus.SUSPENDED, "Ac", 0, 20);

        verify(tenantRepository)
                .findAllExcluding(eq(PlatformTenant.ID), eq(TenantStatus.SUSPENDED), eq("Ac"), eq(0), eq(20));
    }

    @Test
    void supportsNullStatusFilter() {
        when(tenantRepository.findAllExcluding(PlatformTenant.ID, null, null, 0, 20))
                .thenReturn(new PagedResult<>(List.of(), 0, 20, 0, 0));
        when(tenantUsageQuery.findUsage(any())).thenReturn(Map.of());

        assertThat(useCase.list(null, null, 0, 20).content()).isEmpty();
    }

    @Test
    void enrichesEachTenantWithItsUsage() {
        Tenant tenant = someTenant();
        when(tenantRepository.findAllExcluding(any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PagedResult<>(List.of(tenant), 0, 20, 1, 1));
        when(tenantUsageQuery.findUsage(any())).thenReturn(Map.of(tenant.id(), new TenantUsage(7, LAST_ACCESS)));

        TenantSummary summary = useCase.list(null, null, 0, 20).content().getFirst();

        assertThat(summary.tenant()).isSameAs(tenant);
        assertThat(summary.userCount()).isEqualTo(7);
        assertThat(summary.lastAccessAt()).isEqualTo(LAST_ACCESS);
    }

    @Test
    void reportsATenantWithoutUsageAsNeverAccessed() {
        // Un tenant recien creado no tiene sesiones, asi que no aparece en el
        // mapa de uso. Debe listarse igualmente, no desaparecer del listado.
        Tenant tenant = someTenant();
        when(tenantRepository.findAllExcluding(any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PagedResult<>(List.of(tenant), 0, 20, 1, 1));
        when(tenantUsageQuery.findUsage(any())).thenReturn(Map.of());

        TenantSummary summary = useCase.list(null, null, 0, 20).content().getFirst();

        assertThat(summary.userCount()).isZero();
        assertThat(summary.lastAccessAt()).isNull();
    }

    @Test
    void asksForTheUsageOfTheWholePageInASingleCall() {
        // Resolverlo tenant a tenant daria una consulta por fila en cada carga.
        Tenant first = someTenant();
        Tenant second = someTenant();
        when(tenantRepository.findAllExcluding(any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PagedResult<>(List.of(first, second), 0, 20, 2, 1));
        when(tenantUsageQuery.findUsage(any())).thenReturn(Map.of());

        useCase.list(null, null, 0, 20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<UUID>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(tenantUsageQuery).findUsage(captor.capture());
        assertThat(captor.getValue()).containsExactly(first.id(), second.id());
    }

    private Tenant someTenant() {
        return Tenant.register("Acme", "Europe/Madrid", () -> Instant.now(), UUID::randomUUID);
    }
}
