package com.tfp.timetracking.identity.interfaces.rest;

import com.tfp.timetracking.identity.application.RequestPasswordResetCommand;
import com.tfp.timetracking.identity.application.RequestPasswordResetUseCase;
import com.tfp.timetracking.identity.application.ResetPasswordCommand;
import com.tfp.timetracking.identity.application.ResetPasswordUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password")
@Tag(name = "Authentication")
@SecurityRequirements
public class PasswordResetController {

    private static final String ACCEPTED_MESSAGE =
            "Si la cuenta existe y esta operativa, recibiras instrucciones para restablecer la contrasena.";

    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final String cookieName;
    private final String cookiePath;
    private final boolean cookieSecure;

    public PasswordResetController(
            RequestPasswordResetUseCase requestPasswordResetUseCase,
            ResetPasswordUseCase resetPasswordUseCase,
            @Value("${auth.refresh-token.cookie-name:refresh_token}") String cookieName,
            @Value("${auth.refresh-token.cookie-path:/api/v1/auth}") String cookiePath,
            @Value("${auth.refresh-token.cookie-secure:true}") boolean cookieSecure) {
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/forgot")
    @Operation(summary = "Solicita un correo de recuperacion de contrasena")
    public ResponseEntity<PasswordResetAcceptedResponse> forgot(@Valid @RequestBody PasswordForgotRequest request) {
        requestPasswordResetUseCase.request(new RequestPasswordResetCommand(request.email()));
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new PasswordResetAcceptedResponse(ACCEPTED_MESSAGE));
    }

    @PostMapping("/reset")
    @Operation(summary = "Restablece la contrasena usando un token de un solo uso")
    public ResponseEntity<Void> reset(@Valid @RequestBody PasswordResetRequest request) {
        resetPasswordUseCase.reset(new ResetPasswordCommand(request.token(), request.newPassword()));
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(cookiePath)
                .maxAge(Duration.ZERO)
                .build();
    }
}
