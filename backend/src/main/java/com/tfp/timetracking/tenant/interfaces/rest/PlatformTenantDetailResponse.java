package com.tfp.timetracking.tenant.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

/** Detalle de un tenant para la administración de plataforma (RF-TEN-002). */
public record PlatformTenantDetailResponse(
        UUID id,
        String name,
        String status,
        String timezone,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant suspendedAt,
        Instant archivedAt,
        String suspensionReason) {}
