package com.tfp.timetracking.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Fija que el perfil {@code prod} apaga la documentacion viva.
 *
 * <p>Exponer /v3/api-docs y /swagger-ui en internet no abre ningun endpoint
 * ({@link RouteAuthorizationIntegrationTest} vigila eso), pero si publica el
 * inventario completo de rutas, verbos y esquemas de request, incluidos los de
 * plataforma y los de registro y recuperacion de contrasena. Como
 * {@code SharedPublicEndpoints} abre esas rutas sin autenticar en todos los
 * perfiles, lo unico que las cierra en produccion es este apagado.
 *
 * <p>Se lee el YAML en vez de arrancar el contexto con {@code @ActiveProfiles("prod")}
 * a proposito: ese perfil exige DB_URL, DB_USER y demas variables sin valor por
 * defecto, asi que el test acabaria probando el andamiaje que las inyecta en
 * lugar de la configuracion. Lo que se quiere fijar es que el fichero de perfil
 * siga trayendo el apagado, para que un refactor no lo borre en silencio.
 */
class ProductionApiDocsDisabledTest {

    @Test
    void prodProfileDisablesApiDocsAndSwaggerUi() throws IOException {
        PropertySource<?> prodProperties = loadProdProfile();

        assertThat(prodProperties.getProperty("springdoc.api-docs.enabled"))
                .as("springdoc.api-docs.enabled en application-prod.yml")
                .isEqualTo(false);
        assertThat(prodProperties.getProperty("springdoc.swagger-ui.enabled"))
                .as("springdoc.swagger-ui.enabled en application-prod.yml")
                .isEqualTo(false);
    }

    private PropertySource<?> loadProdProfile() throws IOException {
        ClassPathResource resource = new ClassPathResource("application-prod.yml");
        assertThat(resource.exists()).as("application-prod.yml en el classpath").isTrue();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application-prod", resource);
        assertThat(sources).as("documentos YAML cargados").hasSize(1);
        return sources.get(0);
    }
}
