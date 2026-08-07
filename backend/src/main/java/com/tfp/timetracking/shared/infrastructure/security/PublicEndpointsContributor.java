package com.tfp.timetracking.shared.infrastructure.security;

import java.util.List;
import org.springframework.http.HttpMethod;

/**
 * Punto de contribucion de endpoints que NO exigen autenticacion (ADR-0011).
 *
 * <p>Cada modulo declara sus propios endpoints publicos como {@code @Component}
 * en su capa de infraestructura, en lugar de editar la lista literal de
 * {@code permitAll()} de {@link com.tfp.timetracking.shared.infrastructure.SecurityConfig},
 * que pertenece a {@code shared} y es compartida por todos los modulos.
 *
 * <p><b>Esto no relaja la seguridad.</b> La regla sigue siendo
 * {@code anyRequest().authenticated()}: lo unico que cambia es donde se declara
 * la excepcion. La contrapartida es que abrir un endpoint deja de ser visible en
 * un unico fichero, y por eso {@code RouteAuthorizationIntegrationTest} recorre
 * <b>todas</b> las rutas registradas y exige 401 salvo para una lista blanca
 * explicita: cualquier endpoint abierto por descuido rompe ese test.
 *
 * <p>El orden de los filtros de seguridad NO es contribuible y sigue viviendo
 * en {@code SecurityConfig}: es una decision global sobre la cadena, no una
 * declaracion local de un modulo.
 */
public interface PublicEndpointsContributor {

    /**
     * @return endpoints publicos de este modulo; nunca {@code null}
     */
    List<PublicEndpoint> publicEndpoints();

    /**
     * Endpoint publico. Si {@code method} es {@code null} aplica a cualquier
     * verbo HTTP; conviene acotarlo siempre que se pueda (abrir
     * {@code POST /api/v1/auth/login} no debe abrir tambien {@code DELETE}).
     */
    record PublicEndpoint(HttpMethod method, String pattern) {

        public PublicEndpoint {
            if (pattern == null || pattern.isBlank()) {
                throw new IllegalArgumentException("pattern es obligatorio");
            }
        }

        /** Endpoint publico para cualquier verbo HTTP. */
        public static PublicEndpoint any(String pattern) {
            return new PublicEndpoint(null, pattern);
        }

        /** Endpoint publico acotado a un verbo concreto. */
        public static PublicEndpoint of(HttpMethod method, String pattern) {
            return new PublicEndpoint(method, pattern);
        }
    }
}
