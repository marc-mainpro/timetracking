package com.tfp.timetracking.tenant.interfaces.rest;

import jakarta.validation.constraints.Size;

/** Cuerpo del archivado de un tenant: el motivo es opcional (RF-TEN-008). */
public record ArchiveTenantRequest(@Size(max = 500) String reason) {}
