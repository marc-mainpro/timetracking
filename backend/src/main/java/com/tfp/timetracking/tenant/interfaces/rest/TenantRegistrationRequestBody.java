package com.tfp.timetracking.tenant.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/public/tenant-registrations} (RF-REG-002).
 *
 * @param acceptTerms aceptación explícita de los términos; sin ella la
 *     solicitud es inválida (RF-REG-002)
 */
public record TenantRegistrationRequestBody(
        @NotBlank(message = "El nombre de la organización es obligatorio")
                @Size(max = 200, message = "El nombre de la organización es demasiado largo")
                String companyName,
        @NotBlank(message = "La zona horaria es obligatoria") String timezone,
        @NotBlank(message = "El nombre es obligatorio")
                @Size(max = 200, message = "El nombre es demasiado largo")
                String firstName,
        @NotBlank(message = "Los apellidos son obligatorios")
                @Size(max = 200, message = "Los apellidos son demasiado largos")
                String lastName,
        @NotBlank(message = "El email es obligatorio")
                @Email(message = "Email invalido")
                @Size(max = 255, message = "El email es demasiado largo")
                String email,
        @NotBlank(message = "La contraseña es obligatoria")
                @Size(min = 10, max = 200, message = "La contraseña debe tener al menos 10 caracteres")
                String password,
        boolean acceptTerms) {

    /**
     * {@code @JsonIgnore} porque es una regla de validación derivada, no un
     * campo del contrato: sin él Jackson serializaría un {@code termsAccepted}
     * duplicado de {@code acceptTerms}.
     */
    @JsonIgnore
    @AssertTrue(message = "Debes aceptar los términos y condiciones")
    public boolean isTermsAccepted() {
        return acceptTerms;
    }
}
