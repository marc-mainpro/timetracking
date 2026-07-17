CREATE TABLE audit_event (
    id UUID NOT NULL,
    tenant_id UUID,
    actor_user_id UUID,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    correlation_id UUID NOT NULL,
    metadata JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (id)
);

CREATE INDEX ix_audit_event_tenant_occurred_at ON audit_event (tenant_id, occurred_at DESC);
CREATE INDEX ix_audit_event_tenant_action_occurred_at ON audit_event (tenant_id, action, occurred_at DESC);
CREATE INDEX ix_audit_event_correlation_id ON audit_event (correlation_id);
