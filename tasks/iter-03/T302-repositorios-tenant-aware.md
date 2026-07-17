# T302 — Repositorios tenant-aware

- Iteración: 3 · Depende de: T301 · Contexto: CONTEXT-GLOBAL §5

## Objetivo
Garantizar por construcción que toda consulta de negocio filtra por tenant.

## Detalle
1. Convención fijada (KISS, explícita): **todos los métodos de puertos de repositorio de negocio reciben `tenantId` como primer parámetro** (`findById(UUID tenantId, UUID id)`, etc.). Nada de confiar en filtros implícitos mágicos.
2. Refactorizar los puertos y adaptadores existentes (`UserRepository`, `TenantRepository` donde aplique — `TenantRepository` es la excepción: su clave ES el tenant) a esta convención. Los casos de uso obtienen el tenantId de `TenantContext`, nunca del command.
3. Test ArchUnit o de convención adicional: los métodos públicos de interfaces `*Repository` en dominios de negocio (identity, timetracking, corrections, reporting, audit) declaran un parámetro `tenantId` (se puede verificar por reflexión en un test dedicado); documentar excepciones justificadas.
4. Documentar la convención en `docs/architecture/` y en `.skills/review-multitenancy/SKILL.md`.

## Pruebas
- Integración: `findById` con tenant equivocado devuelve vacío aunque el id exista.

## Fuera de alcance
Suite completa cross-tenant (T303).

## Criterios de aceptación
- `mvn verify` verde; convención aplicada y verificada por test.

## Ficheros previstos
Puertos/adaptadores refactorizados, `RepositoryTenantConventionTest.java`, docs.
