package com.tfp.timetracking.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import com.tfp.timetracking.tenant.domain.TenantStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListTenantsUseCaseTest {

    private final TenantRepository tenantRepository = org.mockito.Mockito.mock(TenantRepository.class);
    private final ListTenantsUseCase useCase = new ListTenantsUseCase(tenantRepository);

    @Test
    void delegatesExcludingSystemTenantAndPropagatesFilter() {
        PagedResult<Tenant> expected = new PagedResult<>(List.of(), 0, 20, 0, 0);
        when(tenantRepository.findAllExcluding(PlatformTenant.ID, TenantStatus.SUSPENDED, 0, 20))
                .thenReturn(expected);

        PagedResult<Tenant> result = useCase.list(TenantStatus.SUSPENDED, 0, 20);

        assertThat(result).isSameAs(expected);
        verify(tenantRepository).findAllExcluding(eq(PlatformTenant.ID), eq(TenantStatus.SUSPENDED), eq(0), eq(20));
    }

    @Test
    void supportsNullStatusFilter() {
        PagedResult<Tenant> expected = new PagedResult<>(List.of(), 0, 20, 0, 0);
        when(tenantRepository.findAllExcluding(PlatformTenant.ID, null, 0, 20)).thenReturn(expected);

        assertThat(useCase.list(null, 0, 20)).isSameAs(expected);
    }
}
