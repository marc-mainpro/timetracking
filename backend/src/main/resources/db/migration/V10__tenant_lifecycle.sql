-- Ciclo de vida del tenant V2 (RF-TEN-004, diseño §7). Añade marcas de tiempo
-- del ciclo de vida y el motivo de suspensión, y restringe los estados al
-- conjunto PENDING/ACTIVE/SUSPENDED/ARCHIVED.
--
-- Backfill: los tenants legados en estado 'INACTIVE' (modelo MVP, solo
-- ACTIVE/INACTIVE) pasan a 'SUSPENDED', el estado no operativo reversible del
-- nuevo ciclo de vida. Los 'ACTIVE' se conservan y reciben activated_at =
-- created_at (ya estaban operativos desde su alta).

ALTER TABLE tenant ADD COLUMN activated_at TIMESTAMPTZ;
ALTER TABLE tenant ADD COLUMN suspended_at TIMESTAMPTZ;
ALTER TABLE tenant ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE tenant ADD COLUMN suspension_reason VARCHAR(500);

UPDATE tenant SET status = 'SUSPENDED', suspended_at = updated_at WHERE status = 'INACTIVE';
UPDATE tenant SET activated_at = created_at WHERE status = 'ACTIVE' AND activated_at IS NULL;

ALTER TABLE tenant
    ADD CONSTRAINT ck_tenant_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'ARCHIVED'));

-- Índice para el listado de plataforma filtrado por estado (RF-TEN-001).
CREATE INDEX ix_tenant_status_created_at ON tenant (status, created_at DESC);
