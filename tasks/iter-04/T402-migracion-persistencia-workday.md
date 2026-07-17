# T402 — Migración y persistencia de Workday

- Iteración: 4 · Depende de: T401 · Contexto: CONTEXT-DOMINIO §1, CONTEXT-GLOBAL §5

## Objetivo
Tablas `workday`/`break_entry` y adaptador JPA del agregado con bloqueo optimista.

## Detalle
1. `V3__timetracking.sql`:
   - `workday`: `id UUID PK`, `tenant_id UUID NOT NULL`, `employee_id UUID NOT NULL FK→app_user`, `status VARCHAR(20) NOT NULL`, `started_at TIMESTAMPTZ NOT NULL`, `ended_at TIMESTAMPTZ`, `version BIGINT NOT NULL`, `created_at/updated_at`. Índices `(tenant_id, employee_id, started_at)`. **Índice único parcial** para la invariante: `CREATE UNIQUE INDEX ux_workday_active ON workday(tenant_id, employee_id) WHERE status IN ('OPEN','ON_BREAK')`.
   - `break_entry`: `id UUID PK`, `workday_id UUID NOT NULL FK`, `started_at TIMESTAMPTZ NOT NULL`, `ended_at TIMESTAMPTZ`. Índice único parcial: una pausa abierta por jornada (`WHERE ended_at IS NULL`).
2. Entidades JPA (`WorkdayJpaEntity` con `@Version`, `BreakEntryJpaEntity`), mapper dominio↔JPA (el agregado completo con sus pausas), adaptador que implementa `WorkdayRepository` (tenant-aware según T302: `findActiveByEmployee`, `findById(tenantId,id)`, `findByEmployee(tenantId, employeeId, rango, paginación)`, `findByTenant(...)`, `save`).

## Pruebas (integración, Testcontainers)
Persistir/recargar agregado con pausas; índice único parcial rechaza segunda jornada activa; `OptimisticLockException` al guardar versión obsoleta; queries filtran por tenant.

## Criterios de aceptación
- `mvn verify` verde; migración reproducible.

## Ficheros previstos
`V3__timetracking.sql`, `timetracking/infrastructure/persistence/**`, tests IT.
