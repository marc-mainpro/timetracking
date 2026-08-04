package com.tfp.timetracking.shared.infrastructure;

import com.tfp.timetracking.shared.infrastructure.security.PublicEndpointsContributor;
import com.tfp.timetracking.shared.infrastructure.security.PublicEndpointsContributor.PublicEndpoint;
import com.tfp.timetracking.shared.infrastructure.security.RateLimitFilter;
import com.tfp.timetracking.shared.infrastructure.security.CorrelationIdFilter;
import com.tfp.timetracking.shared.infrastructure.security.RequestSizeLimitFilter;
import com.tfp.timetracking.shared.infrastructure.security.UserStatusFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Configuracion de seguridad HTTP compartida.
 *
 * <p>La regla de fondo es {@code anyRequest().authenticated()}: todo exige
 * Bearer JWT salvo las excepciones que cada modulo declara implementando
 * {@link PublicEndpointsContributor} (ADR-0011). Antes esas excepciones eran
 * una lista literal aqui, lo que obligaba a cada modulo a editar un fichero de
 * {@code shared} para abrir su propio endpoint publico. Hoy {@code shared} solo
 * aporta salud y documentacion, {@code identity} aporta login y refresh, y
 * {@code tenant} el alta publica.
 *
 * <p><b>El orden de los filtros no es contribuible</b> y se mantiene aqui: es
 * una decision global sobre la cadena (correlation id antes que nada, limite de
 * tamano antes del parseo, rate limit antes de autenticar, estado de usuario y
 * tenant despues de autenticar) y dejarla abierta a contribuciones haria que el
 * comportamiento dependiese del orden de arranque de los beans.
 *
 * <p>La configuracion concreta de JWT, CORS y mapeo de claims a authorities la
 * aporta el modulo {@code identity} via beans de Spring, evitando dependencias
 * Java desde {@code shared} a {@code identity} y manteniendo el monolito
 * modular sin ciclos.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            RateLimitFilter rateLimitFilter,
            RequestSizeLimitFilter requestSizeLimitFilter,
            CorrelationIdFilter correlationIdFilter,
            UserStatusFilter userStatusFilter,
            List<PublicEndpointsContributor> publicEndpointsContributors)
            throws Exception {
        List<PublicEndpoint> publicEndpoints = publicEndpointsContributors.stream()
                .flatMap(contributor -> contributor.publicEndpoints().stream())
                .toList();
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin())
                        .referrerPolicy(referrerPolicy -> referrerPolicy.policy(ReferrerPolicy.NO_REFERRER)))
                .addFilterBefore(correlationIdFilter, HeaderWriterFilter.class)
                .addFilterAfter(requestSizeLimitFilter, CorrelationIdFilter.class)
                .addFilterAfter(rateLimitFilter, HeaderWriterFilter.class)
                .addFilterAfter(userStatusFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .authorizeHttpRequests(auth -> {
                    for (PublicEndpoint endpoint : publicEndpoints) {
                        if (endpoint.method() == null) {
                            auth.requestMatchers(endpoint.pattern()).permitAll();
                        } else {
                            auth.requestMatchers(endpoint.method(), endpoint.pattern()).permitAll();
                        }
                    }
                    auth.anyRequest().authenticated();
                });
        return http.build();
    }
}
