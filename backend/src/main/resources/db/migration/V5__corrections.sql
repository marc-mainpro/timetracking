CREATE TABLE correction_request (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    workday_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    reason TEXT NOT NULL,
    proposed_changes JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    resolved_by UUID,
    resolved_at TIMESTAMPTZ,
    resolution_comment TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_correction_request PRIMARY KEY (id),
    CONSTRAINT fk_correction_request_workday FOREIGN KEY (workday_id) REFERENCES workday (id),
    CONSTRAINT fk_correction_request_requested_by FOREIGN KEY (requested_by) REFERENCES app_user (id),
    CONSTRAINT fk_correction_request_resolved_by FOREIGN KEY (resolved_by) REFERENCES app_user (id)
);

CREATE UNIQUE INDEX ux_correction_request_pending ON correction_request (workday_id, requested_by) WHERE status = 'PENDING';
