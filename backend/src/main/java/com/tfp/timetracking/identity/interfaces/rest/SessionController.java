package com.tfp.timetracking.identity.interfaces.rest;

import com.tfp.timetracking.identity.application.ListSessionsUseCase;
import com.tfp.timetracking.identity.application.RevokeAllSessionsUseCase;
import com.tfp.timetracking.identity.application.RevokeSessionUseCase;
import com.tfp.timetracking.shared.application.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/sessions")
@Tag(name = "Authentication")
public class SessionController {

    private final ListSessionsUseCase listSessionsUseCase;
    private final RevokeSessionUseCase revokeSessionUseCase;
    private final RevokeAllSessionsUseCase revokeAllSessionsUseCase;
    private final SessionRestMapper sessionRestMapper;
    private final TenantContext tenantContext;
    private final String cookieName;
    private final String cookiePath;
    private final boolean cookieSecure;

    public SessionController(
            ListSessionsUseCase listSessionsUseCase,
            RevokeSessionUseCase revokeSessionUseCase,
            RevokeAllSessionsUseCase revokeAllSessionsUseCase,
            SessionRestMapper sessionRestMapper,
            TenantContext tenantContext,
            @Value("${auth.refresh-token.cookie-name:refresh_token}") String cookieName,
            @Value("${auth.refresh-token.cookie-path:/api/v1/auth}") String cookiePath,
            @Value("${auth.refresh-token.cookie-secure:true}") boolean cookieSecure) {
        this.listSessionsUseCase = listSessionsUseCase;
        this.revokeSessionUseCase = revokeSessionUseCase;
        this.revokeAllSessionsUseCase = revokeAllSessionsUseCase;
        this.sessionRestMapper = sessionRestMapper;
        this.tenantContext = tenantContext;
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.cookieSecure = cookieSecure;
    }

    @GetMapping
    @Operation(summary = "Lista las sesiones activas del usuario autenticado")
    public List<SessionResponse> list() {
        return sessionRestMapper.toResponses(
                listSessionsUseCase.listCurrentUserSessions(), tenantContext.currentSessionId());
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Revoca una sesion concreta del usuario autenticado")
    public ResponseEntity<Void> revoke(@PathVariable UUID sessionId) {
        boolean revokedCurrent = revokeSessionUseCase.revoke(sessionId);
        if (revokedCurrent) {
            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                    .build();
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Revoca todas las sesiones del usuario autenticado")
    public ResponseEntity<Void> revokeAll() {
        revokeAllSessionsUseCase.revokeAllCurrentUserSessions();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }


    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(cookiePath)
                .maxAge(java.time.Duration.ZERO)
                .build();
    }
}
