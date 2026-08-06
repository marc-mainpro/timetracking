package com.tfp.timetracking.outbox.infrastructure;

import com.tfp.timetracking.outbox.application.IntegrationEventDeliveryException;
import com.tfp.timetracking.outbox.application.IntegrationEventListener;
import com.tfp.timetracking.outbox.application.IntegrationEventPublisher;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.ArrayList;
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
 * <p>El log en si nunca falla: es una operacion local sin causas de negocio.
 * Lo que si puede fallar es la notificacion a los consumidores internos, y en
 * ese caso la excepcion se propaga para que {@code
 * PublishPendingOutboxMessages} programe el reintento correspondiente.
 *
 * <p><strong>T704:</strong> tras loguear, notifica a los {@link
 * IntegrationEventListener} registrados en el contexto.
 *
 * <p><strong>Un fallo en un listener SI se propaga.</strong> Antes se registraba
 * y se tragaba, con el argumento de que la publicacion no debia depender del
 * consumidor de demostracion. Eso dejo de ser cierto en cuanto aparecieron
 * consumidores reales —el correo de verificacion de alta y el de recuperacion de
 * contrasena—: con la excepcion tragada, el mensaje se marcaba
 * {@code PUBLISHED} aunque el correo no se hubiera enviado nunca, de modo que
 * los reintentos que ADR-0012 da por garantizados no llegaban a ejecutarse y el
 * correo se perdia en silencio.
 *
 * <p>Se notifica a <b>todos</b> los listeners antes de propagar, para que el
 * fallo de uno no impida el trabajo de los demas y el resultado no dependa del
 * orden de registro de los beans.
 *
 * <p>Consecuencia asumida: el reintento vuelve a invocar tambien a los listeners
 * que si tuvieron exito, asi que <b>todo listener debe ser idempotente</b>
 * (RF-OUT-005). Es el precio de la entrega at-least-once que ya asume ADR-0005;
 * la alternativa —seguir tragando el error— cambia duplicados por perdidas
 * silenciosas, que es peor.
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
        List<String> failedListeners = new ArrayList<>();
        RuntimeException firstFailure = null;
        for (IntegrationEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ex) {
                failedListeners.add(listener.getClass().getSimpleName());
                if (firstFailure == null) {
                    firstFailure = ex;
                }
                log.warn(
                        "outbox.integration-event.listener-failed listener={} eventId={}",
                        listener.getClass().getName(),
                        event.eventId(),
                        ex);
            }
        }
        if (firstFailure != null) {
            throw new IntegrationEventDeliveryException(event, failedListeners, firstFailure);
        }
    }
}
