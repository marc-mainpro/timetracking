package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.List;

/**
 * Al menos un consumidor interno fallo al procesar un evento de integracion.
 *
 * <p>La lanza el publicador para que {@code PublishPendingOutboxMessages}
 * programe el reintento con backoff y, agotados los intentos, marque el mensaje
 * como {@code FAILED}. Sin ella el mensaje quedaba {@code PUBLISHED} aunque
 * ningun consumidor lo hubiera procesado (RF-OUT-004, ADR-0005, ADR-0012).
 *
 * <p>No hereda de {@code DomainException}: no es una regla de negocio violada
 * por un usuario sino un fallo de proceso en una tarea programada, y nunca
 * viaja al borde REST.
 */
public class IntegrationEventDeliveryException extends RuntimeException {

    private final transient IntegrationEvent event;

    public IntegrationEventDeliveryException(
            IntegrationEvent event, List<String> failedListeners, Throwable cause) {
        super(
                "Fallo la entrega del evento " + event.eventType() + " (" + event.eventId() + ") a: "
                        + String.join(", ", failedListeners),
                cause);
        this.event = event;
    }

    public IntegrationEvent event() {
        return event;
    }
}
