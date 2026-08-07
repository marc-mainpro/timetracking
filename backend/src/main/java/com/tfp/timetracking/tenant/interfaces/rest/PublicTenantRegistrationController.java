package com.tfp.timetracking.tenant.interfaces.rest;

import com.tfp.timetracking.tenant.application.RequestTenantRegistrationUseCase;
import com.tfp.timetracking.tenant.application.ResendTenantRegistrationVerificationUseCase;
import com.tfp.timetracking.tenant.application.VerifyTenantRegistrationEmailUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
 * API pública del alta de organizaciones (RF-REG-001..005, diseño §7.3).
 *
 * <p>Estos endpoints crean una <b>solicitud</b>, nunca un tenant. La creación
 * del tenant —siempre en estado {@code PENDING}— ocurre al aprobar la solicitud
 * desde {@code /api/v1/platform/registrations} (T53-03).
 *
 * <p>Las tres operaciones responden {@code 202 Accepted} con el mismo cuerpo
 * genérico pase lo que pase por dentro (RF-REG-005). El controlador no decide
 * nada: solo comprueba la bandera de configuración y delega.
 */
@RestController
@RequestMapping("/api/v1/public/tenant-registrations")
@Tag(name = "Public - Tenant registrations")
public class PublicTenantRegistrationController {

    private final RequestTenantRegistrationUseCase requestUseCase;
    private final VerifyTenantRegistrationEmailUseCase verifyUseCase;
    private final ResendTenantRegistrationVerificationUseCase resendUseCase;
    private final TenantRegistrationRestMapper mapper;
    private final boolean publicRegistrationEnabled;

    public PublicTenantRegistrationController(
            RequestTenantRegistrationUseCase requestUseCase,
            VerifyTenantRegistrationEmailUseCase verifyUseCase,
            ResendTenantRegistrationVerificationUseCase resendUseCase,
            TenantRegistrationRestMapper mapper,
            @Value("${registration.public.enabled:true}") boolean publicRegistrationEnabled) {
        this.requestUseCase = requestUseCase;
        this.verifyUseCase = verifyUseCase;
        this.resendUseCase = resendUseCase;
        this.mapper = mapper;
        this.publicRegistrationEnabled = publicRegistrationEnabled;
    }

    @PostMapping
    @Operation(summary = "Solicita el alta de una organización (RF-REG-001)")
    public ResponseEntity<TenantRegistrationAcceptedResponse> request(
            @Valid @RequestBody TenantRegistrationRequestBody body, HttpServletRequest request) {
        requirePublicRegistrationEnabled();
        requestUseCase.request(mapper.toCommand(body, request));
        return accepted();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirma la dirección de correo de una solicitud (RF-REG-004)")
    public ResponseEntity<TenantRegistrationAcceptedResponse> verifyEmail(
            @Valid @RequestBody VerifyRegistrationEmailRequest body) {
        requirePublicRegistrationEnabled();
        verifyUseCase.verify(body.token());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new TenantRegistrationAcceptedResponse(
                        "Correo confirmado. Tu solicitud está pendiente de revisión."));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Reenvía el correo de verificación de una solicitud (RF-REG-004)")
    public ResponseEntity<TenantRegistrationAcceptedResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest body) {
        requirePublicRegistrationEnabled();
        resendUseCase.resend(body.email());
        return accepted();
    }

    private void requirePublicRegistrationEnabled() {
        if (!publicRegistrationEnabled) {
            throw new AccessDeniedException("El registro público está deshabilitado");
        }
    }

    private static ResponseEntity<TenantRegistrationAcceptedResponse> accepted() {
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(TenantRegistrationAcceptedResponse.standard());
    }
}
