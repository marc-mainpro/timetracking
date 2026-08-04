package com.tfp.timetracking.tenant.interfaces.rest;

import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.tenant.application.RequestTenantRegistrationCommand;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Traducción entre DTOs del borde REST y el dominio de solicitudes de alta. */
@Component
public class TenantRegistrationRestMapper {

    private static final String PUBLIC_WEB_SOURCE = "PUBLIC_WEB";

    public RequestTenantRegistrationCommand toCommand(TenantRegistrationRequestBody body, HttpServletRequest request) {
        return new RequestTenantRegistrationCommand(
                body.companyName(),
                body.firstName(),
                body.lastName(),
                body.email(),
                body.password(),
                body.timezone(),
                PUBLIC_WEB_SOURCE,
                clientIp(request));
    }

    public TenantRegistrationResponse toResponse(TenantRegistration registration) {
        return new TenantRegistrationResponse(
                registration.id(),
                registration.companyName(),
                registration.ownerFirstName(),
                registration.ownerLastName(),
                registration.email(),
                registration.timezone(),
                registration.status().name(),
                registration.source(),
                registration.decisionReason(),
                registration.createdTenantId(),
                registration.createdAt(),
                registration.verifiedAt(),
                registration.decidedAt());
    }

    public PagedTenantRegistrationsResponse toPagedResponse(PagedResult<TenantRegistration> result) {
        return new PagedTenantRegistrationsResponse(
                result.content().stream().map(this::toResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    /**
     * IP de origen tal como la ve la aplicación. Se resuelve aquí, en el borde,
     * porque es un dato del transporte HTTP y no del dominio; el caso de uso
     * solo recibe una cadena que convierte en huella.
     */
    private static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
