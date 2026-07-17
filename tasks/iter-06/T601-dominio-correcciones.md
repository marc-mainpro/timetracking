# T601 — Dominio CorrectionRequest + migración

- Iteración: 6 · Depende de: T402 · Contexto: CONTEXT-DOMINIO §1 (CorrectionRequest) y §3

## Objetivo
Agregado `CorrectionRequest`, su persistencia y la operación `adjust` de `Workday`.

## Detalle
1. `corrections.domain`: agregado `CorrectionRequest` con factoría `request(tenantId, workdayId, requestedBy, reason, proposedChanges)`; métodos `approve(resolvedBy, comment, now)` / `reject(...)` que validan estado PENDING (resuelta → `CORRECTION_ALREADY_RESOLVED`). VO `ProposedChanges` (nuevos `startedAt`/`endedAt` y pausas; validar coherencia temporal: fin ≥ inicio, pausas dentro de la jornada). Eventos `CorrectionRequested`, `CorrectionApproved`, `CorrectionRejected`.
2. `Workday.adjust(ProposedChanges → cambios aplicables, now)`: aplica cambios de forma controlada, revalida invariantes temporales y pasa a `ADJUSTED` (solo desde CLOSED; corregir jornada abierta no aplica en MVP → rechazo).
3. `V4__corrections.sql`: tabla `correction_request` (`id`, `tenant_id`, `workday_id FK`, `requested_by FK`, `reason TEXT NOT NULL`, `proposed_changes JSONB NOT NULL`, `status VARCHAR(20)`, `resolved_by`, `resolved_at`, `resolution_comment`, `created_at`). Índice único parcial: una PENDING por `(workday_id, requested_by)`.
4. Puerto `CorrectionRequestRepository` tenant-aware + adaptador JPA + mappers.

## Pruebas
- Unitarias: aprobación/rechazo felices; re-resolución → excepción; `ProposedChanges` incoherentes; `Workday.adjust` revalida invariantes; eventos correctos.
- Integración: persistencia, JSONB round-trip, índice único parcial.

## Fuera de alcance
Casos de uso/API (T602), auditoría (T603).

## Criterios de aceptación
- `mvn verify` verde; cobertura dominio ≥90 %.

## Ficheros previstos
`corrections/domain/**`, `corrections/infrastructure/persistence/**`, `V4__corrections.sql`, ajuste en `timetracking/domain/Workday.java`, tests.
