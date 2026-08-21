package com.tfp.timetracking.shared.infrastructure;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declara en el contrato OpenAPI el esquema de seguridad que ya aplica
 * {@link SecurityConfig}: Bearer JWT en la cabecera {@code Authorization}.
 *
 * <p>Sin esta declaracion el documento generado por springdoc no contiene
 * {@code components.securitySchemes}, asi que Swagger UI no muestra el boton
 * <i>Authorize</i> y ninguna operacion protegida puede ejecutarse desde ahi
 * (todas responden 401). El esquema se aplica de forma global porque la regla
 * de fondo del backend es {@code anyRequest().authenticated()}.
 *
 * <p>Las excepciones son los endpoints declarados via
 * {@link com.tfp.timetracking.shared.infrastructure.security.PublicEndpointsContributor},
 * que se marcan en su operacion con {@code @SecurityRequirements} vacio. Esto no
 * es solo cosmetico: si Swagger UI enviase un Bearer caducado a
 * {@code POST /api/v1/auth/login}, el filtro de resource server lo rechazaria
 * con 401 antes de llegar al controlador pese al {@code permitAll()}, y seria
 * imposible reautenticarse sin borrar el token a mano.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "TimeTracking API", version = "v1"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
@SecurityScheme(
        name = OpenApiConfig.BEARER_SCHEME,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access token devuelto por POST /api/v1/auth/login")
public class OpenApiConfig {

    /** Nombre del esquema; referenciado por las operaciones protegidas. */
    public static final String BEARER_SCHEME = "bearerAuth";
}
