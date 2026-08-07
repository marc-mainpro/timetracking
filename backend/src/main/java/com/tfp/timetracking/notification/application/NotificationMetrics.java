package com.tfp.timetracking.notification.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Metricas Micrometer del correo saliente (T140-04, RO-003), con el mismo
 * patron que {@code OutboxMetrics}: contadores creados sobre el
 * {@code MeterRegistry} inyectado y expuestos por Actuator en
 * {@code /actuator/metrics}.
 *
 * <ul>
 *   <li>{@code notification.emails.sent} (contador): correos entregados al
 *       servidor SMTP.
 *   <li>{@code notification.emails.failed} (contador): envios que fallaron. Es
 *       la metrica que se alerta: un correo de verificacion o de recuperacion
 *       que no sale deja a un usuario bloqueado sin que nadie lo note.
 *   <li>{@code notification.emails.skipped} (contador): envios omitidos porque
 *       {@code mail.enabled=false}. Se cuenta aparte de los fallos a proposito:
 *       en un entorno mal configurado, ver este contador subir explica el
 *       "no me llega el correo" mucho antes que revisar logs.
 * </ul>
 *
 * <p>Ningun contador lleva tags con el destinatario ni con el asunto: serian
 * datos personales de alta cardinalidad en un sistema de metricas (RS-014).
 */
@Component
public class NotificationMetrics {

    private final Counter sent;
    private final Counter failed;
    private final Counter skipped;

    public NotificationMetrics(MeterRegistry registry) {
        this.sent = Counter.builder("notification.emails.sent")
                .description("Correos entregados al servidor SMTP")
                .register(registry);
        this.failed = Counter.builder("notification.emails.failed")
                .description("Envios de correo que fallaron")
                .register(registry);
        this.skipped = Counter.builder("notification.emails.skipped")
                .description("Envios omitidos porque el correo esta deshabilitado (mail.enabled=false)")
                .register(registry);
    }

    public void recordSent() {
        sent.increment();
    }

    public void recordFailed() {
        failed.increment();
    }

    public void recordSkipped() {
        skipped.increment();
    }
}
