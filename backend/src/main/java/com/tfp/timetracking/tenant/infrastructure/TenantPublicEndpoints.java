package com.tfp.timetracking.tenant.infrastructure;

import com.tfp.timetracking.shared.infrastructure.security.PublicEndpointsContributor;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * Endpoints publicos del modulo {@code tenant}: el alta publica de
 * organizaciones (RF-REG-001) y su verificacion de correo (RF-REG-004).
 *
 * <p>Que la ruta sea publica en la cadena de seguridad no significa que este
 * operativa: {@code registration.public.enabled} esta en {@code true} por
 * defecto (RF-TEN-010, ADR-0010), pero puede apagarse por configuracion, y con
 * el flag apagado los controladores responden 403. Las rutas se declaran
 * igualmente para que la respuesta la decida la regla de negocio y no un 401
 * generico de la cadena de filtros.
 */
@Component
public class TenantPublicEndpoints implements PublicEndpointsContributor {

    @Override
    public List<PublicEndpoint> publicEndpoints() {
        return List.of(
                // Flujo V2 solicitud -> verificacion -> aprobacion (T53-03).
                PublicEndpoint.of(HttpMethod.POST, "/api/v1/public/tenant-registrations"),
                PublicEndpoint.of(HttpMethod.POST, "/api/v1/public/tenant-registrations/verify-email"),
                PublicEndpoint.of(HttpMethod.POST, "/api/v1/public/tenant-registrations/resend-verification"));
    }
}
