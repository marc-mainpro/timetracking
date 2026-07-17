# Skill: review-multitenancy

## Objetivo

Auditar que un cambio (endpoint, caso de uso, repositorio o migración)
respeta el aislamiento entre tenants antes de darlo por finalizado.

## Entradas

- Código o migración a revisar (diff de la tarea en curso).
- Modelo de amenazas de multitenancy (`docs/security/threat-model.md`).

## Pasos

1. Comprobar que el `tenantId` usado en cualquier query o comando proviene
   de `TenantContext` (derivado del usuario autenticado), nunca de un
   parámetro de request, path variable, header o body.
2. Revisar cada repositorio/consulta tocado: toda query de negocio debe
   incluir el filtro `tenant_id = :tenantId` (o equivalente vía
   especificación/JPQL).
   La convención del proyecto es explícita: el puerto `*Repository` recibe
   `tenantId` como primer parámetro, salvo excepciones documentadas de
   autenticación/plataforma.
3. Revisar cada tabla nueva o modificada: debe tener `tenant_id` y, si
   aplica, restricciones `UNIQUE` compuestas con `tenant_id`.
4. Revisar cada endpoint administrativo: debe comprobar rol Y tenant (un
   `TENANT_ADMIN` de un tenant no puede operar sobre recursos de otro
   tenant).
5. Comprobar que ningún evento de dominio/integración expone datos de un
   tenant hacia un contexto sin `tenantId` propio.

## Validaciones

- Ningún literal de `tenant_id` hardcodeado ni derivado de datos no
  autenticados.
- Ninguna consulta "global" (sin filtro de tenant) sobre tablas de negocio,
  salvo tareas de plataforma explícitamente documentadas (p. ej. jobs de
  sistema) y con ADR si aplica.

## Pruebas

- Al menos un test de integración por endpoint/caso de uso tocado que
  verifique: un usuario del tenant A no puede leer, modificar ni borrar
  datos del tenant B (esperando 403/404, nunca 200 con datos ajenos).
- Test que verifique que un intento de forzar `tenant_id` distinto en el
  payload/JWT manipulado es ignorado o rechazado.
- Toda tarea futura que añada endpoints de negocio debe ampliar la suite
  cross-tenant reutilizable del proyecto con sus nuevos casos de aislamiento.

## Criterios de finalización

- Todos los tests cross-tenant nuevos o existentes en verde.
- No quedan hallazgos abiertos de la lista de validaciones.

## Archivos que puede modificar

- Tests bajo `backend/src/test/java/**` (añadir tests cross-tenant).
- Correcciones puntuales en `application`/`infrastructure` si se detecta una
  fuga (filtro de tenant faltante).

## Archivos que debe actualizar

- `docs/security/threat-model.md` si se detecta y mitiga un riesgo nuevo.
- `tasks/_reports/TXXX-report.md` de la tarea en curso, sección
  "Seguridad".
