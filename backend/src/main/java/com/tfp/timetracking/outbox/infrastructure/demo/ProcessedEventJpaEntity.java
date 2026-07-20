package com.tfp.timetracking.outbox.infrastructure.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de deduplicacion (T704) del consumidor de demostracion. {@code
 * eventId} es la clave primaria: intentar insertar el mismo {@code eventId}
 * dos veces viola la restriccion unica, lo que sirve como red de seguridad
 * ante una condicion de carrera entre dos hilos consumiendo el mismo evento
 * "a la vez" (ver {@link DemoIdempotentEventConsumer}).
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventJpaEntity() {}

    public ProcessedEventJpaEntity(UUID eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
