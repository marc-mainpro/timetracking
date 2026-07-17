-- Tablas de identidad: tenant, app_user, user_role, refresh_token.
-- Ver CONTEXT-GLOBAL §5 (multitenancy) y CONTEXT-DOMINIO §1 (agregados
-- Tenant, User). Roles modelados en tabla user_role(user_id, role) con PK
-- compuesta (decision fijada en la ficha T201).

CREATE TABLE tenant (
    id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    timezone VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tenant PRIMARY KEY (id)
);

CREATE TABLE app_user (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT fk_app_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uq_app_user_tenant_email UNIQUE (tenant_id, email)
);

CREATE INDEX ix_app_user_tenant_id ON app_user (tenant_id);

CREATE TABLE user_role (
    user_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL,
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE refresh_token (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_refresh_token_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_token_user_id ON refresh_token (user_id);
