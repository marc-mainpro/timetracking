package com.tfp.timetracking.shared.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracion minima de seguridad para el scaffolding (T101).
 *
 * <p>Solo se expone {@code /actuator/health}; el resto de endpoints queda
 * cerrado por defecto. La autenticacion JWT (oauth2-resource-server, roles
 * TENANT_ADMIN/EMPLOYEE, etc.) es responsabilidad del modulo {@code identity}
 * y queda fuera de alcance de esta tarea.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
