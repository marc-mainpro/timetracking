package com.tfp.timetracking.shared.infrastructure.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activa el binding de {@link RateLimitProperties}.
 *
 * <p>Vive junto al filtro y no en la clase de arranque: dar de alta la
 * configuracion de una funcionalidad no deberia obligar a editar un fichero
 * compartido por todos los modulos (mismo principio que ADR-0011).
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {}
