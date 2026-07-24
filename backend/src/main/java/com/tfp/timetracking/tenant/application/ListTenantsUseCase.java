package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import com.tfp.timetracking.tenant.domain.TenantStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista los tenants para la administración de plataforma (RF-TEN-001,
 * T50-05), de forma paginada y opcionalmente filtrada por estado. Excluye el
 * tenant de sistema.
 */
@Service
public class ListTenantsUseCase {

    private final TenantRepository tenantRepository;

    public ListTenantsUseCase(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public PagedResult<Tenant> list(TenantStatus status, int page, int size) {
        return tenantRepository.findAllExcluding(PlatformTenant.ID, status, page, size);
    }
}
