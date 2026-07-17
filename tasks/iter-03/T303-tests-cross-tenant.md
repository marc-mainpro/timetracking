# T303 — Suite de pruebas cross-tenant

- Iteración: 3 · Depende de: T302 · Contexto: CONTEXT-GLOBAL §5-6

## Objetivo
Suite de integración reutilizable que demuestre aislamiento total entre tenants, ampliable en iteraciones futuras.

## Detalle
1. Fixture de test reutilizable (`TestTenantFactory` / builder): crea dos tenants A y B con admin y empleado cada uno vía casos de uso reales, y helpers para obtener tokens de cada actor.
2. `CrossTenantSecurityIT` (Testcontainers + MockMvc/RestAssured):
   - Admin de A no ve/gestiona usuarios de B (listados vacíos de B, `GET` por id de B → 404, mutaciones → 404).
   - Login de A no da acceso a recursos de B en ningún endpoint existente.
   - Mismo email en A y B: cada login resuelve a su tenant.
   - Empleado no accede a endpoints de admin → 403.
3. Regla de trabajo documentada: **cada tarea futura con endpoints nuevos debe añadir sus casos a esta suite** (dejarlo anotado en el fichero de la suite y en `.skills/review-multitenancy/SKILL.md`).

## Criterios de aceptación
- `mvn verify` verde; 404 (no 403) al acceder por id a recursos de otro tenant, para no revelar existencia.

## Ficheros previstos
`backend/src/test/java/**/CrossTenantSecurityIT.java`, `TestTenantFactory.java`.
