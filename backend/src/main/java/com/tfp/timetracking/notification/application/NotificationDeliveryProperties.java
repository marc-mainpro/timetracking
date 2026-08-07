package com.tfp.timetracking.notification.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracion del envio de notificaciones (T110-05). */
@ConfigurationProperties(prefix = "notification.delivery")
public record NotificationDeliveryProperties(
        boolean enabled, Duration pollInterval, int batchSize, int maxAttempts) {

    public NotificationDeliveryProperties {
        if (batchSize <= 0) {
            batchSize = 50;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(30);
        }
    }
}
