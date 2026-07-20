package com.tfp.timetracking.outbox.infrastructure;

import com.tfp.timetracking.outbox.application.OutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita {@code @Scheduled} (T703) y enlaza {@link OutboxProperties} desde
 * {@code application.yml} (prefijo {@code outbox.*}).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxSchedulingConfig {}
