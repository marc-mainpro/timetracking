package com.tfp.timetracking.tenant.interfaces.rest;

import java.util.List;

/** Página de tenants para la administración de plataforma (RF-TEN-001). */
public record PagedPlatformTenantsResponse(
        List<PlatformTenantSummaryResponse> content, int page, int size, long totalElements, int totalPages) {}
