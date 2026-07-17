ALTER TABLE app_user DROP CONSTRAINT uq_app_user_tenant_email;

ALTER TABLE app_user
    ADD CONSTRAINT uq_app_user_email UNIQUE (email);
