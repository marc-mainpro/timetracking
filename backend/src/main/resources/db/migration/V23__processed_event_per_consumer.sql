-- La deduplicacion de eventos pasa a ser por (evento, consumidor).
--
-- processed_event nacio en V9 con clave primaria event_id porque solo existia
-- un consumidor: el demo de idempotencia. Desde entonces se han anadido
-- consumidores reales (correo de verificacion de alta y de recuperacion de
-- contrasena) y, sobre todo, el publicador ya propaga el fallo de un listener
-- para que el mensaje se reintente. Con la clave antigua, el primer consumidor
-- que marcase el evento dejaria a los demas creyendo que ya estaba procesado.
--
-- Backfill: las filas existentes pertenecen todas al consumidor de demostracion,
-- unico que escribia en esta tabla hasta ahora.
ALTER TABLE processed_event ADD COLUMN consumer VARCHAR(200);

UPDATE processed_event SET consumer = 'demo-idempotent-event-consumer' WHERE consumer IS NULL;

ALTER TABLE processed_event ALTER COLUMN consumer SET NOT NULL;

ALTER TABLE processed_event DROP CONSTRAINT pk_processed_event;
ALTER TABLE processed_event ADD CONSTRAINT pk_processed_event PRIMARY KEY (event_id, consumer);
