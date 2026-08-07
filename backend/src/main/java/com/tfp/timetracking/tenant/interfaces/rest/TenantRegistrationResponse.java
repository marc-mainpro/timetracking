package com.tfp.timetracking.tenant.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

/**
 * Solicitud de alta vista desde la administración de plataforma.
 *
 * <p>Nunca incluye el hash del token, el hash de la contraseña ni el hash de la
 * IP: ni siquiera un {@code PLATFORM_ADMIN} necesita verlos para decidir.
 */
public record TenantRegistrationResponse(
        UUID id,
        String companyName,
        String ownerFirstName,
        String ownerLastName,
        String email,
        String timezone,
        String status,
        String source,
        String decisionReason,
        UUID createdTenantId,
        Instant createdAt,
        Instant verifiedAt,
        Instant decidedAt) {}
