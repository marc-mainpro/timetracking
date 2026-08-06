package com.tfp.timetracking.tenant.application;

/**
 * Comando de entrada del caso de uso {@link RegisterTenantUseCase}, que hoy
 * invocan la creacion desde plataforma ({@code POST /api/v1/platform/tenants})
 * y la aprobacion de una solicitud de alta. No incluye {@code tenantId} porque
 * el tenant todavia no existe: lo crea este propio caso de uso.
 */
public record RegisterTenantCommand(
        String tenantName,
        String timezone,
        String adminEmail,
        String adminPassword,
        String adminFirstName,
        String adminLastName) {}
