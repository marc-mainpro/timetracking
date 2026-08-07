package com.tfp.timetracking.shared.infrastructure.security;

import com.tfp.timetracking.shared.application.ObservabilityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Abre el contexto de diagnostico de cada peticion HTTP (RNF-020, T140-02).
 *
 * <p>Toma el {@code X-Correlation-Id} entrante o genera uno, lo publica en el
 * MDC (de donde lo recogen los logs, los Problem Details y la auditoria) y lo
 * devuelve en la respuesta para que el cliente pueda citarlo en una incidencia.
 *
 * <p>Es el primer filtro de la cadena (ver {@code SecurityConfig}) para que
 * incluso los rechazos tempranos —429 del rate limit, 413 por tamano, 401 del
 * resource server— salgan correlacionados. Los campos que dependen de haber
 * autenticado ({@code tenantId}, {@code userId}) los anade despues
 * {@code RequestObservabilityInterceptor}.
 *
 * <p>En el {@code finally} se limpia el contexto <b>entero</b>, no solo el
 * identificador de correlacion: los hilos del contenedor se reutilizan entre
 * peticiones y arrastrar un {@code tenantId} de la peticion anterior seria peor
 * que no tener ninguno.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /**
     * Clave MDC del identificador de correlacion. Se mantiene como constante de
     * este filtro porque varios componentes ya la referencian por este nombre;
     * su valor es el declarado en {@link ObservabilityContext}.
     */
    public static final String MDC_KEY = ObservabilityContext.CORRELATION_ID;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (!isValidUuid(correlationId)) {
            correlationId = ObservabilityContext.newCorrelationId();
        }
        ObservabilityContext.put(ObservabilityContext.CORRELATION_ID, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            ObservabilityContext.clear();
        }
    }

    private boolean isValidUuid(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(correlationId);
            return true;
        } catch (IllegalArgumentException invalidUuid) {
            return false;
        }
    }
}
