package com.tfp.timetracking.identity.interfaces.rest;

import com.tfp.timetracking.identity.application.AuthenticateUserCommand;
import com.tfp.timetracking.identity.application.AuthenticateUserUseCase;
import com.tfp.timetracking.identity.application.AuthenticatedSession;
import com.tfp.timetracking.identity.application.LogoutUserCommand;
import com.tfp.timetracking.identity.application.LogoutUserUseCase;
import com.tfp.timetracking.identity.application.RefreshSessionCommand;
import com.tfp.timetracking.identity.application.RefreshSessionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final RefreshSessionUseCase refreshSessionUseCase;
    private final LogoutUserUseCase logoutUserUseCase;
    private final String cookieName;
    private final String cookiePath;
    private final Duration refreshTokenTtl;
    private final boolean cookieSecure;

    public AuthController(
            AuthenticateUserUseCase authenticateUserUseCase,
            RefreshSessionUseCase refreshSessionUseCase,
            LogoutUserUseCase logoutUserUseCase,
            @Value("${auth.refresh-token.cookie-name:refresh_token}") String cookieName,
            @Value("${auth.refresh-token.cookie-path:/api/v1/auth}") String cookiePath,
            @Value("${auth.refresh-token.ttl:P14D}") Duration refreshTokenTtl,
            @Value("${auth.refresh-token.cookie-secure:true}") boolean cookieSecure) {
        this.authenticateUserUseCase = authenticateUserUseCase;
        this.refreshSessionUseCase = refreshSessionUseCase;
        this.logoutUserUseCase = logoutUserUseCase;
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.refreshTokenTtl = refreshTokenTtl;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica un usuario y emite access token + refresh cookie")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        AuthenticatedSession session = authenticateUserUseCase.authenticate(
                new AuthenticateUserCommand(request.email(), request.password()));
        return sessionResponse(session);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rota el refresh token y devuelve un nuevo access token")
    public ResponseEntity<AuthTokenResponse> refresh(
            @CookieValue(name = "${auth.refresh-token.cookie-name:refresh_token}", required = false) String refreshToken) {
        AuthenticatedSession session = refreshSessionUseCase.refresh(new RefreshSessionCommand(refreshToken));
        return sessionResponse(session);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoca el refresh token actual y limpia la cookie")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${auth.refresh-token.cookie-name:refresh_token}", required = false) String refreshToken) {
        logoutUserUseCase.logout(new LogoutUserCommand(refreshToken));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<AuthTokenResponse> sessionResponse(AuthenticatedSession session) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.SET_COOKIE, createRefreshCookie(session.refreshToken()).toString())
                .body(new AuthTokenResponse(session.accessToken(), session.accessTokenExpiresAt()));
    }

    private ResponseCookie createRefreshCookie(String token) {
        return ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(cookiePath)
                .maxAge(refreshTokenTtl)
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
