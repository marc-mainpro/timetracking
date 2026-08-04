package com.tfp.timetracking.tenant.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/platform/registrations/{id}/reject}: motivo obligatorio (diseño §7.5). */
public record RejectRegistrationRequest(
        @NotBlank(message = "El motivo del rechazo es obligatorio")
                @Size(max = 500, message = "El motivo es demasiado largo")
                String reason) {}
