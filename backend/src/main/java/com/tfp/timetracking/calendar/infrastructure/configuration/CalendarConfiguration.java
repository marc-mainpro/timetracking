package com.tfp.timetracking.calendar.infrastructure.configuration;

import com.tfp.timetracking.calendar.application.CalendarProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cableado del modulo {@code calendar}: activa {@link CalendarProperties} a
 * partir de {@code config/calendar.yml} (ADR-0011). El {@code spring.config.import}
 * de ese fichero ya esta declarado en {@code application.yml} como
 * {@code optional:}, asi que no hay que tocar configuracion compartida.
 */
@Configuration
@EnableConfigurationProperties(CalendarProperties.class)
public class CalendarConfiguration {}
