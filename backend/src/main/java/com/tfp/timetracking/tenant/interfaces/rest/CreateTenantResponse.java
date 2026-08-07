package com.tfp.timetracking.tenant.interfaces.rest;

import java.util.UUID;

/** Identificadores del tenant y del administrador recien creados. */
public record CreateTenantResponse(UUID tenantId, UUID adminUserId) {}
