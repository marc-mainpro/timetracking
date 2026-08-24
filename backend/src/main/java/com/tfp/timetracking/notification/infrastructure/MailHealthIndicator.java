package com.tfp.timetracking.notification.infrastructure;

import com.tfp.timetracking.shared.infrastructure.observability.HealthStatuses;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * Salud del servicio de correo saliente (T140-03, RO-001).
 *
 * <p>Tres desenlaces, y el interesante es el tercero:
 *
 * <ul>
 *   <li><b>UP</b>: {@code mail.enabled=true} y el proveedor configurado esta listo.
 *   <li><b>DEGRADED</b>: {@code mail.enabled=true} pero el proveedor falla o falta configuracion.
 *   <li><b>UNKNOWN</b>: {@code mail.enabled=false} (el valor por defecto). El
 *       correo esta deshabilitado a proposito —desarrollo local sin mailpit,
 *       suites de test—, no roto. Marcarlo DOWN dejaria {@code /actuator/health}
 *       en rojo permanente en la configuracion por defecto del proyecto, que es
 *       justo la forma de conseguir que nadie mire nunca la sonda. {@code UNKNOWN}
 *       no arrastra el agregado (con el orden de estados de
 *       {@code config/observability.yml}, UP gana a UNKNOWN) y deja constancia
 *       explicita en los detalles.
 * </ul>
 *
 * <p><b>Por que DEGRADED y no DOWN</b> cuando el proveedor no responde: el envio de
 * correo nunca esta en la transaccion de negocio (ADR-0012) y viaja por el
 * outbox con reintentos, asi que un SMTP caido no impide fichar, aprobar ni
 * consultar nada. Reiniciar el contenedor —que es lo que provoca un DOWN en la
 * sonda— no arreglaria el servidor de correo ajeno y si tiraria la aplicacion.
 *
 * <p>Con SMTP comprueba la conexion con {@code testConnection()}, que abre y
 * cierra la sesion sin enviar nada. Con Resend valida la configuracion minima y
 * deja el detalle explicito: no hace una llamada remota en cada sonda.
 */
@Component
public class MailHealthIndicator implements HealthIndicator {

    private final MailProperties properties;
    private final ObjectProvider<JavaMailSenderImpl> mailSender;

    public MailHealthIndicator(MailProperties properties, ObjectProvider<JavaMailSenderImpl> mailSender) {
        this.properties = properties;
        this.mailSender = mailSender;
    }

    @Override
    public Health health() {
        if (!properties.enabled()) {
            return Health.status(Status.UNKNOWN)
                    .withDetail("enabled", false)
                    .withDetail("reason", "mail.enabled=false: el correo saliente esta deshabilitado")
                    .build();
        }
        if (properties.usesResend()) {
            if (properties.resend().apiKey().isBlank()) {
                return Health.status(HealthStatuses.DEGRADED)
                        .withDetail("enabled", true)
                        .withDetail("provider", "resend")
                        .withDetail("reason", "mail.provider=resend pero falta mail.resend.api-key")
                        .build();
            }
            return Health.up()
                    .withDetail("enabled", true)
                    .withDetail("provider", "resend")
                    .withDetail("baseUrl", properties.resend().baseUrl())
                    .build();
        }
        JavaMailSenderImpl sender = mailSender.getIfAvailable();
        if (sender == null) {
            return Health.status(HealthStatuses.DEGRADED)
                    .withDetail("enabled", true)
                    .withDetail("provider", "smtp")
                    .withDetail("reason", "mail.enabled=true pero no hay JavaMailSender configurado")
                    .build();
        }
        try {
            sender.testConnection();
            return Health.up()
                    .withDetail("enabled", true)
                    .withDetail("provider", "smtp")
                    .withDetail("host", sender.getHost())
                    .withDetail("port", sender.getPort())
                    .build();
        } catch (Exception ex) {
            return Health.status(HealthStatuses.DEGRADED)
                    .withDetail("enabled", true)
                    .withDetail("provider", "smtp")
                    .withDetail("host", sender.getHost())
                    .withDetail("port", sender.getPort())
                    .withDetail("error", ex.getClass().getSimpleName())
                    .build();
        }
    }
}
