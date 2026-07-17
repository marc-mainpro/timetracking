# Agregados

Fuente de verdad: `tasks/_context/CONTEXT-DOMINIO.md` §1.

## Tenant

Campos: `id`, `name`, `status` (`ACTIVE`|`INACTIVE`), `timezone`, `createdAt`,
`updatedAt`.

## User

Campos: `id`, `tenantId`, `email`, `passwordHash`, `firstName`, `lastName`,
`status` (`ACTIVE`|`INACTIVE`), `roles`, `createdAt`, `updatedAt`.

Regla clave: `email` es globalmente único (ADR-0008) para permitir
autenticación por `email + password` sin ambigüedad entre tenants.

## Workday (agregado raíz; contiene BreakEntry)

Campos: `id`, `tenantId`, `employeeId`, `status`, `startedAt`, `endedAt`,
`version` (optimistic locking), `createdAt`, `updatedAt`.

Estados: `OPEN`, `ON_BREAK`, `CLOSED`, `ADJUSTED`.

Transiciones válidas:

- `OPEN → ON_BREAK` (startBreak)
- `ON_BREAK → OPEN` (endBreak)
- `OPEN → CLOSED` (end)
- `CLOSED → ADJUSTED` (corrección aprobada)

Cualquier otra transición lanza una excepción de dominio (HTTP 409).

## BreakEntry (dentro de Workday)

Campos: `id`, `workdayId`, `startedAt`, `endedAt`.

## CorrectionRequest

Campos: `id`, `tenantId`, `workdayId`, `requestedBy`, `reason`,
`proposedChanges` (JSON), `status`, `resolvedBy`, `resolvedAt`,
`resolutionComment`, `createdAt`.

Estados: `PENDING`, `APPROVED`, `REJECTED`.
