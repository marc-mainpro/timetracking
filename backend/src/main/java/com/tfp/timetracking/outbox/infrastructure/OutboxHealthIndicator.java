package com.tfp.timetracking.outbox.infrastructure;

import com.tfp.timetracking.outbox.application.OutboxProperties;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.shared.infrastructure.observability.HealthStatuses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Salud del publicador de outbox (T140-03, RO-001).
 *
 * <p>El outbox es el unico camino por el que salen los eventos de integracion
 * (ADR-0005): si se atasca, la aplicacion sigue respondiendo con normalidad
 * mientras el sistema se desincroniza en silencio. Este indicador convierte ese
 * fallo silencioso en algo consultable.
 *
 * <ul>
 *   <li><b>UP</b>: backlog por debajo de {@code observability.outbox.pending-threshold}
 *       y ningun mensaje {@code FAILED}.
 *   <li><b>DEGRADED</b>: backlog por encima del umbral, o hay mensajes
 *       {@code FAILED} esperando intervencion manual.
 *   <li><b>DOWN</b>: no se puede ni consultar la tabla (normalmente porque la
 *       base de datos no responde; el indicador {@code db} lo confirmara).
 * </ul>
 *
 * <p><b>Por que DEGRADED y no DOWN</b> cuando hay atasco: {@code /actuator/health}
 * es la sonda del contenedor. Un backlog o unos mensajes fallidos no impiden
 * atender peticiones, asi que devolver DOWN provocaria que el orquestador
 * reiniciase una aplicacion sana —y el reinicio no publica ni un mensaje mas—.
 * {@code DEGRADED} esta mapeado a HTTP 200 (ver {@code config/observability.yml}
 * y ADR-0013): se ve en la sonda operativa, pero no recicla el contenedor.
 */
@Component
public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxMessageRepository repository;
    private final OutboxProperties properties;
    private final long pendingThreshold;

    public OutboxHealthIndicator(
            OutboxMessageRepository repository,
            OutboxProperties properties,
            @Value("${observability.outbox.pending-threshold:1000}") long pendingThreshold) {
        this.repository = repository;
        this.properties = properties;
        this.pendingThreshold = pendingThreshold;
    }

    @Override
    public Health health() {
        long pending;
        long failed;
        try {
            pending = repository.countPending();
            failed = repository.countFailed();
        } catch (RuntimeException ex) {
            return Health.down()
                    .withDetail("reason", "No se pudo consultar el estado del outbox")
                    .withDetail("error", ex.getClass().getSimpleName())
                    .build();
        }
        Health.Builder builder =
                pending > pendingThreshold || failed > 0 ? Health.status(HealthStatuses.DEGRADED) : Health.up();
        return builder.withDetail("pending", pending)
                .withDetail("failed", failed)
                .withDetail("pendingThreshold", pendingThreshold)
                .withDetail("maxAttempts", properties.maxAttempts())
                .build();
    }
}
