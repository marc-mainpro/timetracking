package com.tfp.timetracking.shared.infrastructure.observability;

import com.tfp.timetracking.shared.application.ObservabilityContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Completa el contexto de diagnostico de cada peticion HTTP (T140-01, RO-002)
 * con {@code tenantId}, {@code userId}, {@code useCase} y {@code result}.
 *
 * <p>El {@code correlationId} ya lo pone antes
 * {@code CorrelationIdFilter}. El resto no puede ponerlo un filtro colocado al
 * principio de la cadena: cuando ese filtro corre todavia no se ha autenticado
 * a nadie ni se ha resuelto que controlador atendera la peticion. Por eso esto
 * es un {@link HandlerInterceptor} y no un filtro mas: se ejecuta ya dentro del
 * {@code DispatcherServlet}, con {@code SecurityContextHolder} poblado y con el
 * {@link HandlerMethod} resuelto.
 *
 * <p>Consecuencia asumida: las peticiones que mueren antes del dispatcher (401
 * del resource server, 429 del rate limit, 413 por tamano) llevan
 * {@code correlationId} pero no {@code tenantId}/{@code userId} —que en esos
 * casos, precisamente, no existen todavia.
 *
 * <p>Lee el JWT directamente en vez de inyectar {@code TenantContext} porque
 * este interceptor corre tambien en peticiones anonimas y {@code TenantContext}
 * lanza excepcion cuando no hay principal: observar no puede romper la
 * peticion observada.
 *
 * <p><b>RS-014.</b> Solo se publican identificadores opacos y el nombre del
 * caso de uso. Nunca cabeceras (que llevan {@code Authorization} y
 * {@code Cookie}), ni cuerpo, ni query string (que puede llevar filtros con
 * datos personales). El esquema cerrado de {@link ObservabilityContext} impide
 * que se cuele una clave nueva por descuido.
 */
public class RequestObservabilityInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestObservabilityInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        ObservabilityContext.put(ObservabilityContext.USE_CASE, useCaseOf(request, handler));
        Jwt jwt = currentJwt();
        if (jwt != null) {
            ObservabilityContext.put(ObservabilityContext.TENANT_ID, jwt.getClaimAsString("tenantId"));
            ObservabilityContext.put(ObservabilityContext.USER_ID, jwt.getSubject());
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String result = ex == null && response.getStatus() < 500
                ? ObservabilityContext.RESULT_SUCCESS
                : ObservabilityContext.RESULT_FAILURE;
        ObservabilityContext.put(ObservabilityContext.RESULT, result);
        // Linea de resumen de la peticion. El metodo y el status son seguros;
        // la URI se registra sin query string a proposito (RS-014).
        log.debug(
                "http.request method={} path={} status={}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus());
        // No se limpia el MDC aqui: lo hace CorrelationIdFilter, que envuelve a
        // este interceptor y es quien abrio el contexto. Limpiar dos veces no
        // rompe nada, pero dejar la limpieza en un unico sitio evita que una
        // futura linea de log posterior al dispatcher salga sin correlacion.
    }

    private String useCaseOf(HttpServletRequest request, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
        }
        return request.getMethod() + " " + request.getRequestURI();
    }

    private Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken && authentication.isAuthenticated()) {
            return jwtAuthenticationToken.getToken();
        }
        return null;
    }
}
