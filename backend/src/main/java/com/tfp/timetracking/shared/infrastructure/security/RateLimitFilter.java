package com.tfp.timetracking.shared.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.shared.infrastructure.security.RateLimitProperties.EndpointLimit;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limiting en memoria (bucket4j) de los endpoints sensibles (RS-007,
 * T30-03): login, refresh, registro publico, recuperacion de contrasena y
 * reenvio de verificacion.
 *
 * <p>Las reglas ya no son una lista literal de rutas en el codigo sino
 * configuracion por <b>patron</b> ({@link RateLimitProperties}, en
 * {@code config/account-lockout.yml}). Dos motivos: los limites deben ser
 * ajustables sin recompilar —lo pide la ficha T30-03— y varios de los endpoints
 * protegidos aun no existen, asi que el filtro debe cubrirlos por adelantado en
 * cuanto se publiquen.
 *
 * <p>El bucket se indexa por {@code IP + regla} (no por ruta exacta): dos rutas
 * cubiertas por el mismo patron comparten presupuesto, que es lo que se quiere
 * cuando el patron agrupa variantes del mismo flujo sensible.
 *
 * <p><b>Limitacion conocida y aceptada</b> (riesgo residual en
 * {@code docs/security/owasp-review.md}, ADR-0013): los contadores viven en la
 * memoria del proceso. Con varias instancias detras de un balanceador el limite
 * efectivo se multiplica por el numero de replicas, y un reinicio lo reinicia.
 * Es suficiente para el despliegue de instancia unica de la V2 y para frenar
 * fuerza bruta desde una IP; no sustituye a un limitador en el borde. La
 * defensa que no depende de esto es el bloqueo temporal de cuenta (RS-008),
 * que si es persistente y comun a todas las instancias.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int TOO_MANY_REQUESTS = 429;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final List<ResolvedLimit> limits;

    public RateLimitFilter(ObjectMapper objectMapper, RateLimitProperties properties) {
        this.objectMapper = objectMapper;
        this.limits = properties.endpoints().stream()
                .map(endpoint -> ResolvedLimit.from(endpoint, properties))
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return matchingLimit(request).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<ResolvedLimit> limit = matchingLimit(request);
        if (limit.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        ResolvedLimit resolved = limit.get();
        Bucket bucket = buckets.computeIfAbsent(rateLimitKey(request, resolved), key -> newBucket(resolved));
        if (!bucket.tryConsume(1)) {
            writeRateLimitExceeded(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Primera regla que casa metodo y ruta, en el orden declarado en configuracion. */
    private Optional<ResolvedLimit> matchingLimit(HttpServletRequest request) {
        return limits.stream().filter(limit -> limit.matches(request, pathMatcher)).findFirst();
    }

    private Bucket newBucket(ResolvedLimit limit) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(limit.capacity(), Refill.greedy(limit.capacity(), limit.window())))
                .build();
    }

    private String rateLimitKey(HttpServletRequest request, ResolvedLimit limit) {
        return clientIp(request) + '|' + limit.pattern();
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
        body.put("correlationId", currentCorrelationId());
        body.put("timestamp", Instant.now().toString());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String currentCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return correlationId != null ? correlationId : UUID.randomUUID().toString();
    }

    /** Regla con los valores heredados del limite global ya resueltos. */
    private record ResolvedLimit(String method, String pattern, int capacity, Duration window) {

        static ResolvedLimit from(EndpointLimit endpoint, RateLimitProperties properties) {
            return new ResolvedLimit(
                    endpoint.method(),
                    endpoint.pattern(),
                    endpoint.capacity() != null ? endpoint.capacity() : properties.capacity(),
                    endpoint.window() != null ? endpoint.window() : properties.window());
        }

        boolean matches(HttpServletRequest request, AntPathMatcher pathMatcher) {
            if (method != null && !method.equalsIgnoreCase(request.getMethod())) {
                return false;
            }
            return pathMatcher.match(pattern, request.getRequestURI());
        }
    }
}
