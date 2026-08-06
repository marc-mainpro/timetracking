package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.shared.application.TenantUsageQuery;
import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import com.tfp.timetracking.tenant.domain.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista los tenants para la administración de plataforma (RF-TEN-001,
 * T50-05), de forma paginada y opcionalmente filtrada por estado. Excluye el
 * tenant de sistema.
 *
 * <p>Cada fila se acompaña de su número de usuarios y su último acceso, que
 * aporta {@link TenantUsageQuery}. El uso se pide para la página completa en
 * una sola llamada, no tenant a tenant.
 */
@Service
public class ListTenantsUseCase {

    private final TenantRepository tenantRepository;
    private final TenantUsageQuery tenantUsageQuery;

    public ListTenantsUseCase(TenantRepository tenantRepository, TenantUsageQuery tenantUsageQuery) {
        this.tenantRepository = tenantRepository;
        this.tenantUsageQuery = tenantUsageQuery;
    }

    @Transactional(readOnly = true)
    public PagedResult<TenantSummary> list(TenantStatus status, int page, int size) {
        PagedResult<Tenant> tenants = tenantRepository.findAllExcluding(PlatformTenant.ID, status, page, size);
        List<UUID> tenantIds = tenants.content().stream().map(Tenant::id).toList();
        Map<UUID, TenantUsageQuery.TenantUsage> usage = tenantUsageQuery.findUsage(tenantIds);
        List<TenantSummary> summaries = tenants.content().stream()
                .map(tenant -> {
                    TenantUsageQuery.TenantUsage tenantUsage =
                            usage.getOrDefault(tenant.id(), TenantUsageQuery.TenantUsage.NONE);
                    return new TenantSummary(tenant, tenantUsage.userCount(), tenantUsage.lastAccessAt());
                })
                .toList();
        return new PagedResult<>(
                summaries, tenants.page(), tenants.size(), tenants.totalElements(), tenants.totalPages());
    }
}
