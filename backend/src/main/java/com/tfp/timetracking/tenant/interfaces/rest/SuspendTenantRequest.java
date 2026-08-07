package com.tfp.timetracking.tenant.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de la suspensión de un tenant: el motivo es obligatorio (RF-TEN-006). */
public record SuspendTenantRequest(@NotBlank @Size(max = 500) String reason) {}
