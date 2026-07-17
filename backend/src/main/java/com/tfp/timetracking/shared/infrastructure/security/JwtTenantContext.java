package com.tfp.timetracking.shared.infrastructure.security;

import com.tfp.timetracking.shared.application.TenantContext;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtTenantContext implements TenantContext {

    @Override
    public UUID currentTenantId() {
        return UUID.fromString(requireJwt().getClaimAsString("tenantId"));
    }

    @Override
    public UUID currentUserId() {
        return UUID.fromString(requireJwt().getSubject());
    }

    @Override
    public Set<String> currentRoles() {
        java.util.List<String> roles = requireJwt().getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalStateException("JWT sin claim roles");
        }
        return Set.copyOf(roles);
    }

    private Jwt requireJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No hay autenticacion JWT activa");
        }
        Jwt jwt = jwtAuthenticationToken.getToken();
        if (jwt.getSubject() == null || jwt.getClaimAsString("tenantId") == null) {
            throw new IllegalStateException("JWT sin claims obligatorios");
        }
        return jwt;
    }
}
