package com.tfp.timetracking.tenant.interfaces.rest;

import com.tfp.timetracking.tenant.application.RegisterTenantCommand;
import com.tfp.timetracking.tenant.application.RegisterTenantResult;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/auth/register} (CONTEXT-API §2): endpoint publico que
 * registra una organizacion (tenant) junto a su primer usuario
 * {@code TENANT_ADMIN}. Sin logica de negocio aqui: solo mapea DTO -> comando
 * -> resultado -> DTO (skill {@code create-rest-endpoint}).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthRegisterController {

    private final RegisterTenantUseCase registerTenantUseCase;

    public AuthRegisterController(RegisterTenantUseCase registerTenantUseCase) {
        this.registerTenantUseCase = registerTenantUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterTenantResponse> register(@Valid @RequestBody RegisterTenantRequest request) {
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
