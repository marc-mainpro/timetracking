package com.tfp.timetracking.shared.infrastructure.security;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

/**
 * Limites de peticiones por endpoint (RS-007, T30-03).
 *
 * <p>Las reglas se declaran por <b>patron de ruta</b>, no por ruta literal, de
 * modo que los endpoints sensibles que aun no existen —recuperacion de
 * contrasena y reenvio de verificacion, que llegan en olas posteriores— queden
 * cubiertos desde el momento en que se publiquen y no dependan de que alguien
 * se acuerde de anadirlos a una lista.
 *
 * <p>{@code capacity} y {@code window} son opcionales en cada regla: cuando
 * faltan se hereda el valor global {@code auth.rate-limit.capacity/window}. Asi
 * un ajuste del limite general sigue afectando a los endpoints que no han
 * pedido un valor propio.
 *
 * @param capacity numero de peticiones permitidas por ventana (valor por defecto)
 * @param window ventana de recarga del bucket (valor por defecto)
 * @param endpoints reglas concretas; la primera que casa es la que se aplica
 */
@ConfigurationProperties(prefix = "auth.rate-limit")
public record RateLimitProperties(int capacity, Duration window, List<EndpointLimit> endpoints) {

    public RateLimitProperties {
        if (capacity < 1) {
            throw new IllegalArgumentException("auth.rate-limit.capacity debe ser al menos 1");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("auth.rate-limit.window debe ser positiva");
        }
        // Sin reglas configuradas se cae al minimo historico (login y registro)
        // en vez de dejar todo sin limitar: perder el fichero de configuracion
        // no debe traducirse en desactivar una defensa en silencio.
        endpoints = endpoints == null || endpoints.isEmpty() ? MINIMUM_ENDPOINTS : List.copyOf(endpoints);
    }

    private static final List<EndpointLimit> MINIMUM_ENDPOINTS = List.of(
            new EndpointLimit("POST", "/api/v1/auth/login", null, null),
            new EndpointLimit("POST", "/api/v1/auth/register", null, null));

    /**
     * @param method metodo HTTP al que aplica la regla; {@code null} para todos
     * @param pattern patron Ant de ruta, p. ej. {@code /api/v1/auth/password/**}
     * @param capacity limite propio, o {@code null} para heredar el global
     * @param window ventana propia, o {@code null} para heredar la global
     */
    public record EndpointLimit(String method, String pattern, Integer capacity, Duration window) {

        private static final Set<String> KNOWN_METHODS = Arrays.stream(HttpMethod.values())
                .map(HttpMethod::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        public EndpointLimit {
            method = method == null || method.isBlank() ? null : method.trim().toUpperCase(Locale.ROOT);
            if (method != null && !KNOWN_METHODS.contains(method)) {
                throw new IllegalArgumentException(
                        "auth.rate-limit.endpoints[].method no es un metodo HTTP valido: " + method);
            }
            if (pattern == null || pattern.isBlank()) {
                throw new IllegalArgumentException("auth.rate-limit.endpoints[].pattern es obligatorio");
            }
            if (capacity != null && capacity < 1) {
                throw new IllegalArgumentException("auth.rate-limit.endpoints[].capacity debe ser al menos 1");
            }
            if (window != null && (window.isNegative() || window.isZero())) {
                throw new IllegalArgumentException("auth.rate-limit.endpoints[].window debe ser positiva");
            }
        }
    }
}
