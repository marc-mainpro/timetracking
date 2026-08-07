CREATE TABLE user_session (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    user_agent_hash VARCHAR(64),
    ip_hash VARCHAR(64),
    CONSTRAINT pk_user_session PRIMARY KEY (id),
    CONSTRAINT fk_user_session_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_user_session_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE INDEX ix_user_session_tenant_user ON user_session (tenant_id, user_id);
CREATE INDEX ix_user_session_user_id ON user_session (user_id);
CREATE INDEX ix_user_session_active ON user_session (tenant_id, user_id, revoked_at, expires_at);

ALTER TABLE refresh_token ADD COLUMN session_id UUID;
ALTER TABLE refresh_token ADD CONSTRAINT fk_refresh_token_session FOREIGN KEY (session_id) REFERENCES user_session (id);
CREATE INDEX ix_refresh_token_session_id ON refresh_token (session_id);
