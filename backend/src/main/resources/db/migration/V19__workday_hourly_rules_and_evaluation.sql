CREATE TABLE hourly_rules (
    tenant_id UUID NOT NULL,
    max_daily_work_minutes INTEGER,
    required_break_minutes INTEGER,
    CONSTRAINT pk_hourly_rules PRIMARY KEY (tenant_id),
    CONSTRAINT fk_hourly_rules_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT ck_hourly_rules_max_daily_work_positive CHECK (max_daily_work_minutes IS NULL OR max_daily_work_minutes > 0),
    CONSTRAINT ck_hourly_rules_required_break_non_negative CHECK (required_break_minutes IS NULL OR required_break_minutes >= 0)
);

CREATE TABLE workday_evaluation (
    workday_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    expected_minutes BIGINT NOT NULL,
    worked_minutes BIGINT NOT NULL,
    paused_minutes BIGINT NOT NULL,
    overtime_minutes BIGINT NOT NULL,
    anomalies VARCHAR(300) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_workday_evaluation PRIMARY KEY (workday_id),
    CONSTRAINT fk_workday_evaluation_workday FOREIGN KEY (workday_id) REFERENCES workday (id) ON DELETE CASCADE,
    CONSTRAINT fk_workday_evaluation_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT ck_workday_evaluation_expected_non_negative CHECK (expected_minutes >= 0),
    CONSTRAINT ck_workday_evaluation_worked_non_negative CHECK (worked_minutes >= 0),
    CONSTRAINT ck_workday_evaluation_paused_non_negative CHECK (paused_minutes >= 0),
    CONSTRAINT ck_workday_evaluation_overtime_non_negative CHECK (overtime_minutes >= 0)
);

CREATE INDEX ix_workday_evaluation_tenant_employee ON workday_evaluation (tenant_id, employee_id);
