package com.tfp.timetracking.tenant.application;

import java.util.UUID;

/** Resultado del caso de uso {@link RegisterTenantUseCase}: ids creados, sin datos sensibles. */
public record RegisterTenantResult(UUID tenantId, UUID adminUserId) {}
