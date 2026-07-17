ALTER TABLE correction_request
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
