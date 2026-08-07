package com.tfp.timetracking.identity.infrastructure;

import com.tfp.timetracking.identity.application.PasswordResetProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordResetConfig {}
