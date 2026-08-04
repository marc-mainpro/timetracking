package com.tfp.timetracking.notification.infrastructure;

import com.tfp.timetracking.shared.infrastructure.observability.HealthStatuses;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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
 *   <li><b>UP</b>: {@code mail.enabled=true} y el SMTP acepta la conexion.
 *   <li><b>DEGRADED</b>: {@code mail.enabled=true} pero el SMTP no responde.
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
 * <p><b>Por que DEGRADED y no DOWN</b> cuando el SMTP no responde: el envio de
 * correo nunca esta en la transaccion de negocio (ADR-0012) y viaja por el
 * outbox con reintentos, asi que un SMTP caido no impide fichar, aprobar ni
 * consultar nada. Reiniciar el contenedor —que es lo que provoca un DOWN en la
 * sonda— no arreglaria el servidor de correo ajeno y si tiraria la aplicacion.
 *
 * <p>Comprueba la conexion con {@code testConnection()}, que abre y cierra la
 * sesion SMTP sin enviar nada: no le llega un correo de prueba a nadie.
 */
@Component
public class MailHealthIndicator implements HealthIndicator {

    private final boolean enabled;
    private final ObjectProvider<JavaMailSenderImpl> mailSender;

    public MailHealthIndicator(
            @Value("${mail.enabled:false}") boolean enabled, ObjectProvider<JavaMailSenderImpl> mailSender) {
        this.enabled = enabled;
        this.mailSender = mailSender;
    }

    @Override
    public Health health() {
        if (!enabled) {
            return Health.status(Status.UNKNOWN)
                    .withDetail("enabled", false)
                    .withDetail("reason", "mail.enabled=false: el correo saliente esta deshabilitado")
                    .build();
        }
        JavaMailSenderImpl sender = mailSender.getIfAvailable();
        if (sender == null) {
            return Health.status(HealthStatuses.DEGRADED)
                    .withDetail("enabled", true)
                    .withDetail("reason", "mail.enabled=true pero no hay JavaMailSender configurado")
                    .build();
        }
        try {
            sender.testConnection();
            return Health.up()
                    .withDetail("enabled", true)
                    .withDetail("host", sender.getHost())
                    .withDetail("port", sender.getPort())
                    .build();
        } catch (Exception ex) {
            return Health.status(HealthStatuses.DEGRADED)
                    .withDetail("enabled", true)
                    .withDetail("host", sender.getHost())
                    .withDetail("port", sender.getPort())
                    .withDetail("error", ex.getClass().getSimpleName())
                    .build();
        }
    }
}
