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
 * {@code POST /api/v1/auth/register}: alta directa de una organización,
 * <b>heredada del MVP y en desuso</b> (ADR-0016).
 *
 * <p>Crea el tenant ya {@code ACTIVE} en un solo paso, que es justo lo que el
 * diseño §7.3 y el criterio T53-03 descartan para el alta pública. El flujo
 * público válido en la V2 es
 * {@code POST /api/v1/public/tenant-registrations}: crea una solicitud, exige
 * verificación de correo y deja la creación del tenant —siempre en
 * {@code PENDING}— en manos del {@code PLATFORM_ADMIN}.
 *
 * <p>Se conserva porque la batería de tests de todos los módulos arranca sus
 * tenants por aquí ({@code TestTenantFactory}) y migrarla es un cambio
 * transversal que no corresponde a esta épica; queda documentado en el
 * {@code HANDOFF.md} como deuda a retirar. Sigue deshabilitado por defecto
 * (RF-TEN-010): con {@code registration.public.enabled=false} responde 403.
 *
 * @deprecated usar {@code POST /api/v1/public/tenant-registrations} (T53-03) o,
 *     para el alta manual, {@code POST /api/v1/platform/tenants} (RF-TEN-003).
 */
@Deprecated(since = "V2")
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
