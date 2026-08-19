-- Supresion manual de elementos fallidos de las colas desde el panel de
-- plataforma.
--
-- Descartar NO borra la fila: el requisito es conservar la traza de que algo
-- fallo y de que alguien decidio renunciar a el. La fila se queda con su
-- last_error intacto y pasa a DISCARDED, de modo que deja de contar como
-- incidencia pendiente sin perder la evidencia. El motivo y el actor viven en
-- audit_event, no aqui: el panel no muestra los descartados, asi que una
-- columna de motivo seria un dato que nadie lee.
--
-- DISCARDED no es CANCELLED: cancelar es anular un aviso PENDING porque el
-- hecho dejo de ser relevante; descartar es abandonar el reintento de un envio
-- que ya agoto sus intentos. Mezclarlos borraria justo la distincion util.
ALTER TABLE notification
    DROP CONSTRAINT ck_notification_status;

ALTER TABLE notification
    ADD CONSTRAINT ck_notification_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED', 'DISCARDED'));

-- outbox_message.status no tiene CHECK (V8), asi que el estado nuevo no
-- requiere tocar la restriccion: solo el enum del dominio.

-- El panel lista los fallidos por antiguedad y no habia indice que sirviera ese
-- orden: el indice existente de outbox es (status, next_attempt_at), pensado
-- para la reclamacion del publicador, no para este listado. Parciales, como
-- ix_notification_pending: los FAILED son una minoria de ambas tablas.
CREATE INDEX ix_outbox_message_failed
    ON outbox_message (created_at)
    WHERE status = 'FAILED';

CREATE INDEX ix_notification_failed
    ON notification (created_at)
    WHERE status = 'FAILED';
