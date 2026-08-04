-- Solicitud de alta pública de tenant (T53-01/T53-02, RF-REG-001..006).
--
-- La tabla es deliberadamente independiente de `tenant`: una solicitud existe
-- ANTES de que exista el tenant, y puede no llegar a existir nunca (rechazada,
-- caducada). Por eso no tiene columna tenant_id, sino created_tenant_id, que
-- solo se rellena cuando la solicitud se consume (estado CONSUMED) y apunta al
-- tenant creado en estado PENDING (T53-03: nunca se crea un tenant ACTIVE
-- directamente desde el registro público).
--
-- Del token de verificación solo se guarda su hash SHA-256 en hexadecimal
-- (64 caracteres, RS-014): un volcado de esta tabla no permite construir un
-- enlace de verificación válido. Lo mismo con la IP de origen: ip_hash, nunca
-- la dirección en claro (minimización de datos personales).

CREATE TABLE tenant_registration (
    id UUID NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    owner_first_name VARCHAR(200) NOT NULL,
    owner_last_name VARCHAR(200) NOT NULL,
    email VARCHAR(255) NOT NULL,
    owner_password_hash VARCHAR(255) NOT NULL,
    timezone VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL,
    verification_token_hash VARCHAR(64),
    verification_token_expires_at TIMESTAMPTZ,
    verification_sent_at TIMESTAMPTZ,
    resend_count INTEGER NOT NULL DEFAULT 0,
    source VARCHAR(40) NOT NULL,
    ip_hash VARCHAR(64),
    decision_reason VARCHAR(500),
    created_tenant_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    decided_at TIMESTAMPTZ,
    CONSTRAINT pk_tenant_registration PRIMARY KEY (id),
    CONSTRAINT fk_tenant_registration_created_tenant FOREIGN KEY (created_tenant_id) REFERENCES tenant (id),
    CONSTRAINT ck_tenant_registration_status CHECK (status IN (
        'PENDING_EMAIL_VERIFICATION',
        'PENDING_REVIEW',
        'APPROVED',
        'REJECTED',
        'EXPIRED',
        'CONSUMED')),
    CONSTRAINT ck_tenant_registration_resend_count CHECK (resend_count >= 0),
    -- Una solicitud consumida tiene siempre tenant creado, y solo ella lo tiene.
    CONSTRAINT ck_tenant_registration_consumed_has_tenant CHECK (
        (status = 'CONSUMED' AND created_tenant_id IS NOT NULL)
        OR (status <> 'CONSUMED' AND created_tenant_id IS NULL))
);

-- Un token vivo identifica como mucho una solicitud (verificación en un solo
-- uso). El índice es parcial porque el hash se borra al consumirse y NULL no
-- debe colisionar consigo mismo.
CREATE UNIQUE INDEX ux_tenant_registration_verification_token_hash
    ON tenant_registration (verification_token_hash)
    WHERE verification_token_hash IS NOT NULL;

-- Búsqueda de la solicitud viva de un correo y límite de solicitudes por correo
-- (RF-REG-003).
CREATE INDEX ix_tenant_registration_email_created_at
    ON tenant_registration (email, created_at DESC);

-- Listado de revisión de plataforma filtrado por estado.
CREATE INDEX ix_tenant_registration_status_created_at
    ON tenant_registration (status, created_at DESC);

-- Límite de solicitudes por IP (RF-REG-003).
CREATE INDEX ix_tenant_registration_ip_hash_created_at
    ON tenant_registration (ip_hash, created_at DESC);
