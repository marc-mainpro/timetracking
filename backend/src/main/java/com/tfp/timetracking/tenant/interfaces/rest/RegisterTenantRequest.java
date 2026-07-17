package com.tfp.timetracking.tenant.interfaces.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de request de {@code POST /api/v1/auth/register} (CONTEXT-API §2).
 * Separado del modelo de dominio y de las entidades JPA (CONTEXT-GLOBAL §4).
 */
public record RegisterTenantRequest(
        @NotBlank(message = "El nombre de la organizacion es obligatorio") String tenantName,
        @NotBlank(message = "La zona horaria es obligatoria") String timezone,
        @NotBlank(message = "El email es obligatorio") @Email(message = "Email invalido") String adminEmail,
        @NotBlank(message = "La contraseña es obligatoria")
                @Size(min = 10, message = "La contraseña debe tener al menos 10 caracteres")
                String adminPassword,
        @NotBlank(message = "El nombre es obligatorio") String firstName,
        @NotBlank(message = "El apellido es obligatorio") String lastName) {}
