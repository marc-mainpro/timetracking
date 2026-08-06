-- T110-02: notificaciones dirigidas a un usuario (RF-NOT-001, RF-NOT-006).
--
-- Reune el aviso que el usuario ve en la aplicacion (read_at) y su entrega por
-- correo (status, attempts, last_error): es el mismo hecho, y separarlos
-- obligaria a mantener sincronizados dos ciclos de vida.
--
-- recipient_email se guarda desnormalizado a proposito: la notificacion es una
-- foto del momento del hecho, asi que un cambio posterior de correo no debe
-- redirigir un aviso ya emitido.
CREATE TABLE notification (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    recipient_email VARCHAR(320),
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED'))
);

-- Listado del usuario: siempre por tenant + destinatario y ordenado por fecha.
CREATE INDEX ix_notification_tenant_recipient_created_at
    ON notification (tenant_id, recipient_user_id, created_at DESC);

-- Cola de envio: el job solo mira las pendientes, que son una minoria frente al
-- historico acumulado, asi que el indice es parcial.
CREATE INDEX ix_notification_pending
    ON notification (created_at)
    WHERE status = 'PENDING';
