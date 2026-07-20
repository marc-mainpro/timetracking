package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.shared.domain.IntegrationEvent;

/**
 * Puerto de salida del publicador de outbox (T703, ADR-0005). Entrega un
 * {@link IntegrationEvent} ya reclamado de la tabla {@code outbox_message} a
 * su destino final.
 *
 * <p>ADR-0005 prohibe introducir un broker de mensajeria (Kafka/RabbitMQ/...)
 * en el MVP; la unica implementacion de este puerto es un log estructurado
 * ({@code outbox.infrastructure.LoggingIntegrationEventPublisher}). Adoptar
 * un broker real en el futuro solo cambiaria la implementacion de este
 * puerto (y requeriria una nueva ADR); el resto del publicador
 * ({@code PublishPendingOutboxMessages}, reintentos, backoff, metricas) no
 * cambiaria.
 */
public interface IntegrationEventPublisher {

    /**
     * Publica el evento. Debe lanzar una excepcion (no devolver un booleano
     * ni tragarse el error) si la entrega falla, para que
     * {@code PublishPendingOutboxMessages} pueda decidir reintento/backoff o
     * marcar el mensaje como fallido definitivamente.
     */
    void publish(IntegrationEvent event);
}
