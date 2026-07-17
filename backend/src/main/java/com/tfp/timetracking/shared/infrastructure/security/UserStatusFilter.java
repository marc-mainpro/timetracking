package com.tfp.timetracking.shared.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.shared.application.AuthenticatedPrincipalStateChecker;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.DomainException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class UserStatusFilter extends OncePerRequestFilter {

    private final TenantContext tenantContext;
    private final AuthenticatedPrincipalStateChecker authenticatedPrincipalStateChecker;
    private final ObjectMapper objectMapper;

    public UserStatusFilter(
            TenantContext tenantContext,
            AuthenticatedPrincipalStateChecker authenticatedPrincipalStateChecker,
            ObjectMapper objectMapper) {
        this.tenantContext = tenantContext;
        this.authenticatedPrincipalStateChecker = authenticatedPrincipalStateChecker;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            authenticatedPrincipalStateChecker.ensureActivePrincipal(
                    tenantContext.currentTenantId(), tenantContext.currentUserId());
            filterChain.doFilter(request, response);
        } catch (DomainException ex) {
            writeUnauthorized(response, ex.errorCode(), ex.getMessage());
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String errorCode, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "Unauthorized");
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("detail", detail);
        body.put("errorCode", errorCode);
        body.put("correlationId", currentCorrelationId());
        body.put("timestamp", Instant.now().toString());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String currentCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return correlationId != null ? correlationId : java.util.UUID.randomUUID().toString();
    }
}
