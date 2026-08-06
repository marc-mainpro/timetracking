package com.tfp.timetracking.tenant.interfaces.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta manual de un tenant por un {@code PLATFORM_ADMIN}
 * ({@code POST /api/v1/platform/tenants}, RF-TEN-003).
 *
 * <p>Es la unica via que crea un tenant ya operativo sin pasar por la
 * aprobacion de una solicitud: la decide una persona con rol de plataforma, que
 * es precisamente el control que el alta publica no tiene.
 */
public record CreateTenantRequest(
        @NotBlank(message = "El nombre de la organizacion es obligatorio") String tenantName,
        @NotBlank(message = "La zona horaria es obligatoria") String timezone,
        @NotBlank(message = "El email es obligatorio") @Email(message = "Email invalido") String adminEmail,
        @NotBlank(message = "La contraseña es obligatoria")
                @Size(min = 10, message = "La contraseña debe tener al menos 10 caracteres")
                String adminPassword,
        @NotBlank(message = "El nombre es obligatorio") String firstName,
        @NotBlank(message = "El apellido es obligatorio") String lastName) {}
