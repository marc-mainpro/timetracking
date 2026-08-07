package com.tfp.timetracking.tenant.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/public/tenant-registrations/verify-email} (T53-05). */
public record VerifyRegistrationEmailRequest(
        @NotBlank(message = "El token es obligatorio")
                @Size(max = 200, message = "El token no es válido")
                String token) {}
