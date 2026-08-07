package com.tfp.timetracking.shared.infrastructure.observability;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra {@link RequestObservabilityInterceptor} en el {@code
 * DispatcherServlet} (T140-01).
 *
 * <p>Se registra como interceptor y no como filtro de seguridad a proposito:
 * el orden de la cadena de filtros es una decision global que vive en
 * {@code SecurityConfig} (ADR-0011) y no admite contribuciones; el registro de
 * interceptores MVC, en cambio, es un punto de extension estandar y aditivo.
 */
@Configuration
public class ObservabilityWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestObservabilityInterceptor()).addPathPatterns("/**");
    }
}
