package com.tfp.timetracking.tenant.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

/**
 * Resumen de un tenant para el listado de plataforma (RF-TEN-001).
 *
 * @param userCount usuarios del tenant, activos o no
 * @param lastAccessAt último uso de una sesión del tenant; {@code null} si nunca
 *     se ha accedido
 */
public record PlatformTenantSummaryResponse(
        UUID id,
        String name,
        String status,
        String timezone,
        Instant createdAt,
        Instant activatedAt,
        Instant suspendedAt,
        long userCount,
        Instant lastAccessAt) {}
