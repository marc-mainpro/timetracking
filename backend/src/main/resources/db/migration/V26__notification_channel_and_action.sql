-- T170-02: canal de entrega y enlace accionable de la notificacion.
--
-- Hasta ahora toda notificacion pendiente se enviaba por correo. Con el fan-out
-- por rol hay avisos que solo tienen sentido dentro de la aplicacion (la
-- anomalia de jornada del equipo llenaria el buzon del administrador), asi que
-- el canal pasa a ser un dato de la fila.
--
-- El canal se persiste en lugar de resolverse en memoria porque la cola de
-- envio filtra por SQL: si el emisor descartara las notificaciones sin correo
-- despues de recuperarlas, volverian en cada pasada y envenenarian la cola.
ALTER TABLE notification
    ADD COLUMN email_required BOOLEAN NOT NULL DEFAULT TRUE,
    -- Ruta del frontend a la que lleva la notificacion (p. ej. /admin/corrections).
    -- Nullable: hay avisos meramente informativos que no llevan a ninguna parte.
    ADD COLUMN action_path VARCHAR(200);

-- El indice parcial de la cola tiene que reflejar el nuevo filtro: sin esto, el
-- job leeria tambien las filas que nunca va a enviar.
DROP INDEX ix_notification_pending;

CREATE INDEX ix_notification_pending
    ON notification (created_at)
    WHERE status = 'PENDING' AND email_required;
