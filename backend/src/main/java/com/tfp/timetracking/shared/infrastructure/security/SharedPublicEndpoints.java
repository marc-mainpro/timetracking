package com.tfp.timetracking.shared.infrastructure.security;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * Endpoints publicos transversales: sonda de salud y documentacion de la API.
 *
 * <p>{@code /actuator/health} se expone sin autenticacion porque lo consulta el
 * healthcheck de Docker Compose antes de que exista ninguna credencial. No
 * filtra informacion: {@code management.endpoint.health.show-details} es
 * {@code never}, asi que responde unicamente {@code {"status":"UP"}}.
 */
@Component
public class SharedPublicEndpoints implements PublicEndpointsContributor {

    @Override
    public List<PublicEndpoint> publicEndpoints() {
        return List.of(
                PublicEndpoint.of(HttpMethod.GET, "/actuator/health"),
                PublicEndpoint.of(HttpMethod.GET, "/actuator/health/**"),
                PublicEndpoint.of(HttpMethod.GET, "/v3/api-docs"),
                PublicEndpoint.of(HttpMethod.GET, "/v3/api-docs/**"),
                PublicEndpoint.of(HttpMethod.GET, "/swagger-ui.html"),
                PublicEndpoint.of(HttpMethod.GET, "/swagger-ui/**"));
    }
}
