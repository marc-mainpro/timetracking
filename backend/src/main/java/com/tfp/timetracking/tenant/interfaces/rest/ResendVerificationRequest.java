package com.tfp.timetracking.tenant.interfaces.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/public/tenant-registrations/resend-verification} (T53-05). */
public record ResendVerificationRequest(
        @NotBlank(message = "El email es obligatorio")
                @Email(message = "Email invalido")
                @Size(max = 255, message = "El email es demasiado largo")
                String email) {}
