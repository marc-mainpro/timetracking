package com.tfp.timetracking.tenant.infrastructure;

import com.tfp.timetracking.tenant.application.RegistrationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita el enlace de {@link RegistrationProperties} con
 * {@code config/registration.yml} (T53-04). Vive en {@code infrastructure} por
 * la misma razón que {@code OutboxSchedulingConfig}: es cableado de Spring, no
 * lógica de aplicación.
 */
@Configuration
@EnableConfigurationProperties(RegistrationProperties.class)
public class RegistrationConfig {}
