package com.tfp.timetracking.tenant.infrastructure;

import com.tfp.timetracking.shared.infrastructure.security.PublicEndpointsContributor;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * Endpoints publicos del modulo {@code tenant}: el alta publica de
 * organizaciones (RF-REG-001).
 *
 * <p>Que la ruta sea publica en la cadena de seguridad no significa que este
 * operativa: {@code registration.public.enabled} esta en {@code false} por
 * defecto (RF-TEN-010, ADR-0010) y con el flag apagado el controlador responde
 * 403. La ruta se declara igualmente para que la respuesta la decida la regla
 * de negocio y no un 401 generico de la cadena de filtros.
 */
@Component
public class TenantPublicEndpoints implements PublicEndpointsContributor {

    @Override
    public List<PublicEndpoint> publicEndpoints() {
        return List.of(PublicEndpoint.of(HttpMethod.POST, "/api/v1/auth/register"));
    }
}
