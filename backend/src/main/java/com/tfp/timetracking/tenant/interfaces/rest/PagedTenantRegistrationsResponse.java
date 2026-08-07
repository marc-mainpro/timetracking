package com.tfp.timetracking.tenant.interfaces.rest;

import java.util.List;

/** Página de solicitudes de alta para la administración de plataforma. */
public record PagedTenantRegistrationsResponse(
        List<TenantRegistrationResponse> content, int page, int size, long totalElements, int totalPages) {}
