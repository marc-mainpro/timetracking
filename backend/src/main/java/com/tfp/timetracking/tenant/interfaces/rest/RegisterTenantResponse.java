package com.tfp.timetracking.tenant.interfaces.rest;

import java.util.UUID;

/**
 * DTO de response de {@code POST /api/v1/auth/register}: solo ids, sin datos
 * sensibles (ni password, ni passwordHash).
 */
public record RegisterTenantResponse(UUID tenantId, UUID adminUserId) {}
