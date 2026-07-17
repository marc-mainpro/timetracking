package com.tfp.timetracking.shared.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limiting en memoria para endpoints publicos de autenticacion sensibles.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int TOO_MANY_REQUESTS = 429;

    private static final Set<String> PROTECTED_PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/register");

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final int capacity;
    private final Duration window;

    public RateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${auth.rate-limit.capacity:10}") int capacity,
            @Value("${auth.rate-limit.window:PT1M}") Duration window) {
        this.objectMapper = objectMapper;
        this.capacity = capacity;
        this.window = window;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Bucket bucket = buckets.computeIfAbsent(rateLimitKey(request), key -> newBucket());
        if (!bucket.tryConsume(1)) {
            writeRateLimitExceeded(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(capacity, window)))
                .build();
    }

    private String rateLimitKey(HttpServletRequest request) {
        return clientIp(request) + '|' + request.getRequestURI();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitExceeded(HttpServletResponse response) throws IOException {
        response.setStatus(TOO_MANY_REQUESTS);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "Too Many Requests");
        body.put("status", TOO_MANY_REQUESTS);
        body.put("detail", "Se ha excedido el numero maximo de intentos para este endpoint");
        body.put("errorCode", "RATE_LIMIT_EXCEEDED");
        body.put("correlationId", UUID.randomUUID().toString());
        body.put("timestamp", Instant.now().toString());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
