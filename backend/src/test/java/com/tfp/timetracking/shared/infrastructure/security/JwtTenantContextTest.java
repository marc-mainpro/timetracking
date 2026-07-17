package com.tfp.timetracking.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtTenantContextTest {

    private final JwtTenantContext tenantContext = new JwtTenantContext();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesTenantUserAndRolesFromJwtClaims() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(authentication(userId, tenantId, List.of("TENANT_ADMIN")));

        assertThat(tenantContext.currentTenantId()).isEqualTo(tenantId);
        assertThat(tenantContext.currentUserId()).isEqualTo(userId);
        assertThat(tenantContext.currentRoles()).containsExactly("TENANT_ADMIN");
    }

    @Test
    void failsWithoutAuthentication() {
        assertThatThrownBy(tenantContext::currentTenantId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenTenantClaimIsMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("roles", List.of("EMPLOYEE"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES));

        assertThatThrownBy(tenantContext::currentTenantId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenRolesClaimIsMissing() {
        UUID tenantId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("tenantId", tenantId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES));

        assertThatThrownBy(tenantContext::currentRoles).isInstanceOf(IllegalStateException.class);
    }

    private JwtAuthenticationToken authentication(UUID userId, UUID tenantId, List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("tenantId", tenantId.toString())
                .claim("roles", roles)
                .build();
        return new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES);
    }
}
