CREATE TABLE workday (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_workday PRIMARY KEY (id),
    CONSTRAINT fk_workday_employee FOREIGN KEY (employee_id) REFERENCES app_user (id)
);

CREATE INDEX ix_workday_tenant_employee_started_at ON workday (tenant_id, employee_id, started_at);
CREATE UNIQUE INDEX ux_workday_active ON workday (tenant_id, employee_id) WHERE status IN ('OPEN', 'ON_BREAK');

CREATE TABLE break_entry (
    id UUID NOT NULL,
    workday_id UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    CONSTRAINT pk_break_entry PRIMARY KEY (id),
    CONSTRAINT fk_break_entry_workday FOREIGN KEY (workday_id) REFERENCES workday (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_break_entry_open ON break_entry (workday_id) WHERE ended_at IS NULL;
