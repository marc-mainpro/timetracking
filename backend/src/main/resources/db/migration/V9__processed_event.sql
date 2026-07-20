-- T704: tabla de deduplicacion para el consumidor de ejemplo idempotente
-- (com.tfp.timetracking.outbox.infrastructure.demo.DemoIdempotentEventConsumer).
--
-- No es una tabla de negocio: demuestra el patron de idempotencia que ADR-0005
-- exige a los futuros consumidores reales de los eventos de integracion
-- publicados por el outbox (entrega at-least-once). event_id coincide con el
-- eventId del envelope IntegrationEvent (= id de la fila en outbox_message),
-- por lo que sirve como clave de deduplicacion estable ante redelivery.
CREATE TABLE processed_event (
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id)
);
