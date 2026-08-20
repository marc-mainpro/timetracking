-- Repara los tenants que se quedaron sin catálogo de tipos de ausencia
-- (RF-ABS-001).
--
-- Los tipos son tenant-scoped, ninguna migración los sembraba y no hay endpoint
-- que los cree: el único mecanismo es SeedDefaultAbsenceTypesListener. Ese
-- listener sembraba sobre el tenantId del envelope, pero los eventos del
-- registro público se emiten con el tenant de plataforma (el id del tenant real
-- viaja en payload.tenantId). Resultado: todo tenant nacido por alta pública se
-- quedaba con el catálogo vacío y sus empleados no podían solicitar nada.
--
-- El listener ya toma el tenant del payload; esta migración arregla los tenants
-- ya creados. Cubre además la restauración de un dump anterior al listener, que
-- tampoco pasa por eventos.
--
-- Los códigos, nombres y flags deben coincidir con DEFAULT_TYPES de
-- SeedDefaultAbsenceTypesListener.

-- 1. Retira el catálogo que la siembra errónea dejó en el tenant de plataforma,
--    que no es un tenant de negocio y no solicita ausencias. Solo si ninguna
--    solicitud lo referencia, para no chocar con fk_absence_request_type.
DELETE FROM absence_type t
WHERE t.tenant_id = '00000000-0000-0000-0000-000000000001'
  AND NOT EXISTS (
      SELECT 1 FROM absence_request r WHERE r.absence_type_id = t.id
  );

-- 2. Siembra el catálogo por defecto en todo tenant de negocio que no tenga
--    ningún tipo. Idempotente: el NOT EXISTS deja fuera a los que ya lo tienen
--    y uq_absence_type_tenant_code protege del resto.
INSERT INTO absence_type (id, tenant_id, code, name, requires_approval, allows_attachment, active)
SELECT gen_random_uuid(), t.id, d.code, d.name, d.requires_approval, false, true
FROM tenant t
CROSS JOIN (
    VALUES
        ('VACACIONES', 'Vacaciones', true),
        ('PERMISO', 'Permiso', true),
        ('BAJA', 'Baja médica', true),
        ('JUSTIFICADA', 'Ausencia justificada', true),
        ('NO_JUSTIFICADA', 'Ausencia no justificada', false)
) AS d (code, name, requires_approval)
WHERE t.id <> '00000000-0000-0000-0000-000000000001'
  AND NOT EXISTS (
      SELECT 1 FROM absence_type existing WHERE existing.tenant_id = t.id
  );
