CREATE TABLE absence_type (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    requires_approval BOOLEAN NOT NULL,
    allows_attachment BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT pk_absence_type PRIMARY KEY (id),
    CONSTRAINT fk_absence_type_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uq_absence_type_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX ix_absence_type_tenant_id ON absence_type (tenant_id);

CREATE TABLE absence_request (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    absence_type_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    resolved_by UUID,
    resolved_at TIMESTAMPTZ,
    resolution_comment VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_absence_request PRIMARY KEY (id),
    CONSTRAINT fk_absence_request_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_absence_request_employee FOREIGN KEY (employee_id) REFERENCES app_user (id),
    CONSTRAINT fk_absence_request_type FOREIGN KEY (absence_type_id) REFERENCES absence_type (id),
    CONSTRAINT ck_absence_request_range CHECK (end_date >= start_date)
);

CREATE INDEX ix_absence_request_tenant_employee_date ON absence_request (tenant_id, employee_id, start_date, end_date);
CREATE INDEX ix_absence_request_tenant_date ON absence_request (tenant_id, start_date, end_date);
