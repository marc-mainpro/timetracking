package com.tfp.timetracking.tenant.interfaces.rest;

import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.tenant.application.TenantSummary;
import com.tfp.timetracking.tenant.domain.Tenant;
import org.springframework.stereotype.Component;

/** Traduce el agregado {@link Tenant} a los DTO de respuesta de plataforma. */
@Component
public class PlatformTenantRestMapper {

    public PlatformTenantSummaryResponse toSummary(TenantSummary summary) {
        Tenant tenant = summary.tenant();
        return new PlatformTenantSummaryResponse(
                tenant.id(),
                tenant.name(),
                tenant.status().name(),
                tenant.timezone(),
                tenant.createdAt(),
                tenant.activatedAt(),
                tenant.suspendedAt(),
                summary.userCount(),
                summary.lastAccessAt());
    }

    public PlatformTenantDetailResponse toDetail(Tenant tenant) {
        return new PlatformTenantDetailResponse(
                tenant.id(),
                tenant.name(),
                tenant.status().name(),
                tenant.timezone(),
                tenant.createdAt(),
                tenant.updatedAt(),
                tenant.activatedAt(),
                tenant.suspendedAt(),
                tenant.archivedAt(),
                tenant.suspensionReason());
    }

    public PagedPlatformTenantsResponse toPagedResponse(PagedResult<TenantSummary> result) {
        return new PagedPlatformTenantsResponse(
                result.content().stream().map(this::toSummary).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
