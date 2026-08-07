package com.tfp.timetracking.identity.infrastructure.security;

import com.tfp.timetracking.shared.infrastructure.security.PublicEndpointsContributor;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * Endpoints publicos del modulo {@code identity}: los dos puntos de entrada de
 * autenticacion, que por definicion se invocan sin token.
 *
 * <p>Ambos estan acotados a {@code POST} y protegidos por
 * {@link com.tfp.timetracking.shared.infrastructure.security.RateLimitFilter}
 * (RS-007). {@code /refresh} no lleva token Bearer pero si la cookie
 * {@code HttpOnly} del refresh token, que es la credencial que valida.
 */
@Component
public class IdentityPublicEndpoints implements PublicEndpointsContributor {

    @Override
    public List<PublicEndpoint> publicEndpoints() {
        return List.of(
                PublicEndpoint.of(HttpMethod.POST, "/api/v1/auth/login"),
                PublicEndpoint.of(HttpMethod.POST, "/api/v1/auth/refresh"),
                PublicEndpoint.of(HttpMethod.POST, "/api/v1/auth/password/forgot"),
                PublicEndpoint.of(HttpMethod.POST, "/api/v1/auth/password/reset"));
    }
}
