CREATE TABLE shift_template (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    planned_break_minutes INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_shift_template PRIMARY KEY (id),
    CONSTRAINT fk_shift_template_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uq_shift_template_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_shift_template_break_non_negative CHECK (planned_break_minutes >= 0),
    CONSTRAINT ck_shift_template_non_zero_duration CHECK (start_time <> end_time)
);

CREATE INDEX ix_shift_template_tenant_id ON shift_template (tenant_id);

CREATE TABLE shift_assignment (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    shift_template_id UUID NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    CONSTRAINT pk_shift_assignment PRIMARY KEY (id),
    CONSTRAINT fk_shift_assignment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_shift_assignment_employee FOREIGN KEY (employee_id) REFERENCES app_user (id),
    CONSTRAINT fk_shift_assignment_template FOREIGN KEY (shift_template_id) REFERENCES shift_template (id),
    CONSTRAINT ck_shift_assignment_period CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX ix_shift_assignment_tenant_employee ON shift_assignment (tenant_id, employee_id, valid_from, valid_to);
