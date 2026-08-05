package com.tfp.timetracking.identity.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "auth.password-reset")
public record PasswordResetProperties(
        @DefaultValue("PT1H") Duration tokenTtl,
        @DefaultValue("http://localhost:4200/restablecer-contrasena?token=%s") String resetUrlTemplate) {}
