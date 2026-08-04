-- Bloqueo temporal de cuentas tras intentos fallidos (T30-04, RF-USR-008,
-- RS-008, diseño §8.5).
--
-- Tabla propia y no columnas en app_user (ADR-0013): el contador se escribe en
-- cada intento de login fallido, un camino de alta frecuencia y controlado por
-- el atacante. Mantenerlo aquí evita reescribir la fila del usuario —y competir
-- por su bloqueo optimista— en cada intento, y permite purgar el histórico sin
-- tocar el agregado User.
--
-- La PK es user_id: existe como mucho una fila de bloqueo por usuario. Se
-- guarda también tenant_id, redundante con app_user, para que toda consulta
-- pueda ser tenant-scoped sin un JOIN (CONTEXT-GLOBAL §5).

CREATE TABLE account_lockout (
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    last_failed_attempt_at TIMESTAMPTZ,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_account_lockout PRIMARY KEY (user_id),
    CONSTRAINT fk_account_lockout_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_account_lockout_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT ck_account_lockout_failed_attempts CHECK (failed_attempts >= 0)
);

-- Consulta de soporte: cuentas actualmente bloqueadas de un tenant.
CREATE INDEX ix_account_lockout_tenant_locked_until ON account_lockout (tenant_id, locked_until);
