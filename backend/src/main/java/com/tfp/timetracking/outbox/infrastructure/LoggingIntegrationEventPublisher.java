package com.tfp.timetracking.outbox.infrastructure;

import com.tfp.timetracking.outbox.application.IntegrationEventPublisher;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Unica implementacion MVP del puerto {@link IntegrationEventPublisher}
 * (T703, ADR-0005): un log estructurado. ADR-0005 prohibe expresamente
 * introducir un broker de mensajeria real (Kafka/RabbitMQ/...) en el MVP;
 * esta clase es deliberadamente el unico "consumidor" externo hasta que una
 * ADR posterior decida lo contrario.
 *
 * <p>Nunca lanza excepcion: registrar en el log es una operacion local que
 * no falla por causas de negocio. Si en el futuro esta implementacion se
 * sustituye por una que si pueda fallar (HTTP a un consumidor real, por
 * ejemplo), debe propagar la excepcion para que {@code
 * PublishPendingOutboxMessages} programe el reintento correspondiente.
 */
@Component
public class LoggingIntegrationEventPublisher implements IntegrationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingIntegrationEventPublisher.class);

    @Override
    public void publish(IntegrationEvent event) {
        log.info(
                "outbox.integration-event eventId={} eventType={} eventVersion={} tenantId={} "
                        + "aggregateType={} aggregateId={} occurredAt={}",
                event.eventId(),
                event.eventType(),
                event.eventVersion(),
                event.tenantId(),
                event.aggregateType(),
                event.aggregateId(),
                event.occurredAt());
    }
}
