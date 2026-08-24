package com.tfp.timetracking.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Configuracion del adaptador de correo saliente. */
@ConfigurationProperties(prefix = "mail")
public record MailProperties(boolean enabled, String provider, String from, Resend resend) {

    private static final String SMTP = "smtp";
    private static final String RESEND = "resend";

    public MailProperties {
        provider = StringUtils.hasText(provider) ? provider.trim().toLowerCase() : SMTP;
        if (!SMTP.equals(provider) && !RESEND.equals(provider)) {
            throw new IllegalArgumentException("mail.provider debe ser 'smtp' o 'resend'");
        }
        resend = resend == null ? new Resend("https://api.resend.com", "") : resend;
    }

    public boolean usesResend() {
        return RESEND.equals(provider);
    }

    public record Resend(String baseUrl, String apiKey) {

        public Resend {
            baseUrl = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "https://api.resend.com";
            apiKey = apiKey == null ? "" : apiKey.trim();
        }
    }
}
