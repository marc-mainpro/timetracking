package com.tfp.timetracking.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del correo de notificacion (T170-09).
 *
 * @param appBaseUrl base absoluta del frontend con la que se compone el enlace
 *     de la notificacion. El backend no puede deducirla: la misma aplicacion se
 *     sirve en dominios distintos por entorno.
 */
@ConfigurationProperties(prefix = "notification.email")
public record NotificationEmailProperties(String appBaseUrl) {

    public NotificationEmailProperties {
        appBaseUrl = normalize(appBaseUrl);
    }

    /**
     * Quita la barra final para que unir base y ruta no produzca {@code //}, y
     * deja {@code null} si no hay base configurada: en ese caso el correo sale
     * sin enlace en lugar de con uno roto.
     */
    private static String normalize(String appBaseUrl) {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return null;
        }
        String trimmed = appBaseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
