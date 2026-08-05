ALTER TABLE hourly_rules
    ADD COLUMN rounding_step_minutes INTEGER,
    ADD COLUMN tolerance_minutes INTEGER,
    ADD CONSTRAINT ck_hourly_rules_rounding_step_positive CHECK (rounding_step_minutes IS NULL OR rounding_step_minutes > 0),
    ADD CONSTRAINT ck_hourly_rules_tolerance_non_negative CHECK (tolerance_minutes IS NULL OR tolerance_minutes >= 0);

ALTER TABLE workday_evaluation
    ADD COLUMN effective_worked_minutes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN deviation_minutes BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_workday_evaluation_effective_worked_non_negative CHECK (effective_worked_minutes >= 0),
    ADD CONSTRAINT ck_workday_evaluation_deviation_non_negative CHECK (deviation_minutes >= 0);

UPDATE workday_evaluation
SET effective_worked_minutes = worked_minutes,
    deviation_minutes = 0;
