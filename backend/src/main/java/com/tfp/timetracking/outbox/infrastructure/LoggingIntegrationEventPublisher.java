package com.tfp.timetracking.outbox.infrastructure;

import com.tfp.timetracking.outbox.application.IntegrationEventListener;
import com.tfp.timetracking.outbox.application.IntegrationEventPublisher;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.List;
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
 *
 * <p><strong>T704:</strong> tras loguear, notifica a los {@link
 * IntegrationEventListener} registrados en el contexto (en el MVP, solo el
 * consumidor de demostracion idempotente, ver
 * {@code outbox.infrastructure.demo.DemoIdempotentEventConsumer}). Un fallo
 * en un listener se registra pero nunca se propaga: la publicacion
 * (log + marcar {@code PUBLISHED}) no depende de que la demostracion tenga
 * exito.
 */
@Component
public class LoggingIntegrationEventPublisher implements IntegrationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingIntegrationEventPublisher.class);

    private final List<IntegrationEventListener> listeners;

    public LoggingIntegrationEventPublisher(List<IntegrationEventListener> listeners) {
        this.listeners = listeners;
    }

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
        notifyListeners(event);
    }

    private void notifyListeners(IntegrationEvent event) {
        for (IntegrationEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ex) {
                log.warn(
                        "outbox.integration-event.listener-failed listener={} eventId={}",
                        listener.getClass().getName(),
                        event.eventId(),
                        ex);
            }
        }
    }
}
