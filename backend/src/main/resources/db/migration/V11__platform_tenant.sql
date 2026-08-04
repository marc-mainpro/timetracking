-- Tenant de sistema para los usuarios PLATFORM_ADMIN (T50-04). Id fijo,
-- coincidente con shared.domain.PlatformTenant.ID. No es un tenant de negocio:
-- se excluye del listado de plataforma (RF-TEN-001). El usuario PLATFORM_ADMIN
-- concreto NO se crea aquí (su contraseña es un secreto): lo aprovisiona de
-- forma idempotente PlatformAdminBootstrap a partir de configuración/entorno.

INSERT INTO tenant (id, name, status, timezone, created_at, updated_at, activated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Plataforma',
    'ACTIVE',
    'UTC',
    now(),
    now(),
    now()
)
ON CONFLICT (id) DO NOTHING;
