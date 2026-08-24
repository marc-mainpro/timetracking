package com.tfp.timetracking.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Configuracion del adaptador de correo saliente. */
@ConfigurationProperties(prefix = "mail")
public record MailProperties(boolean enabled, String provider, String from, Resend resend) {

    public MailProperties {
        provider = StringUtils.hasText(provider) ? provider.trim().toLowerCase() : "smtp";
        resend = resend == null ? new Resend("https://api.resend.com", "") : resend;
    }

    public boolean usesResend() {
        return "resend".equals(provider);
    }

    public record Resend(String baseUrl, String apiKey) {

        public Resend {
            baseUrl = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "https://api.resend.com";
            apiKey = apiKey == null ? "" : apiKey.trim();
        }
    }
}
