package com.tfp.timetracking.tenant.interfaces.rest;

import com.tfp.timetracking.tenant.application.RegisterTenantCommand;
import com.tfp.timetracking.tenant.application.RegisterTenantResult;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/auth/register}: registro público de una organización.
 *
 * <p>Deshabilitado por defecto (RF-TEN-010): la creación de tenants es una
 * operación de plataforma reservada al {@code PLATFORM_ADMIN}
 * ({@code POST /api/v1/platform/tenants}). Este endpoint solo responde cuando
 * {@code registration.public.enabled=true}; en caso contrario devuelve 403. Se
 * mantiene tras una bandera de configuración para poder reactivar el alta
 * autoservicio sin cambios de código si el producto lo requiere.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthRegisterController {

    private final RegisterTenantUseCase registerTenantUseCase;
    private final boolean publicRegistrationEnabled;

    public AuthRegisterController(
            RegisterTenantUseCase registerTenantUseCase,
            @Value("${registration.public.enabled:false}") boolean publicRegistrationEnabled) {
        this.registerTenantUseCase = registerTenantUseCase;
        this.publicRegistrationEnabled = publicRegistrationEnabled;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterTenantResponse> register(@Valid @RequestBody RegisterTenantRequest request) {
        if (!publicRegistrationEnabled) {
            throw new AccessDeniedException("El registro público está deshabilitado");
        }
        RegisterTenantResult result = registerTenantUseCase.register(new RegisterTenantCommand(
                request.tenantName(),
                request.timezone(),
                request.adminEmail(),
                request.adminPassword(),
                request.firstName(),
                request.lastName()));

        RegisterTenantResponse body = new RegisterTenantResponse(result.tenantId(), result.adminUserId());
        URI location = URI.create("/api/v1/tenants/" + result.tenantId());
        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
    }
}
