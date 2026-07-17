# CONTEXT-DOMINIO — Modelo, invariantes y eventos

Leer junto a CONTEXT-GLOBAL en toda tarea de backend con lógica de negocio.

## 1. Agregados

### Tenant
Campos: `id`, `name`, `status` (ACTIVE|INACTIVE), `timezone`, `createdAt`, `updatedAt`.
Reglas: nombre obligatorio; timezone IANA válida; un tenant inactivo no puede operar (ninguna operación de negocio).

### User
Campos: `id`, `tenantId`, `email`, `passwordHash`, `firstName`, `lastName`, `status` (ACTIVE|INACTIVE), `roles`, `createdAt`, `updatedAt`.
Reglas: email único **dentro del tenant**; usuario inactivo no se autentica; un usuario pertenece a un único tenant.

### Workday (agregado raíz; contiene BreakEntry)
Campos: `id`, `tenantId`, `employeeId`, `status`, `startedAt`, `endedAt`, `version` (optimistic locking), `createdAt`, `updatedAt`.
Estados: `OPEN`, `ON_BREAK`, `CLOSED`, `ADJUSTED`.

Invariantes (cada una con test unitario):
1. Solo una jornada abierta (OPEN u ON_BREAK) por empleado.
2. No iniciar pausa sin jornada activa (OPEN).
3. No iniciar segunda pausa (ya ON_BREAK).
4. No cerrar jornada con pausa abierta.
5. No cerrar jornada ya cerrada (CLOSED/ADJUSTED).
6. Hora de fichaje = hora del servidor (nunca del cliente).
7. Cambios históricos SOLO vía corrección aprobada (estado pasa a ADJUSTED).

Transiciones válidas: `OPEN → ON_BREAK` (startBreak), `ON_BREAK → OPEN` (endBreak), `OPEN → CLOSED` (end), `CLOSED → ADJUSTED` (corrección aprobada). Todo lo demás → excepción de dominio → HTTP 409.

### BreakEntry (dentro de Workday)
Campos: `id`, `workdayId`, `startedAt`, `endedAt`.
Reglas: pertenece a una jornada; `endedAt >= startedAt`; solo una pausa abierta (`endedAt IS NULL`) por jornada.

### CorrectionRequest
Campos: `id`, `tenantId`, `workdayId`, `requestedBy`, `reason`, `proposedChanges` (JSON: nuevos startedAt/endedAt y pausas), `status`, `resolvedBy`, `resolvedAt`, `resolutionComment`, `createdAt`.
Estados: `PENDING`, `APPROVED`, `REJECTED`.
Reglas: solo una solicitud PENDING por jornada y usuario; una solicitud resuelta no se re-resuelve (→409); toda aprobación genera registro de auditoría Y aplica los cambios a la jornada de forma controlada (jornada → ADJUSTED).

## 2. Excepciones de dominio → errorCode

Definir jerarquía `DomainException` (sin Spring). Códigos estables mínimos:
`TENANT_INACTIVE`, `USER_INACTIVE`, `EMAIL_ALREADY_IN_USE`, `WORKDAY_ALREADY_OPEN`, `WORKDAY_NOT_OPEN`, `WORKDAY_OPEN_BREAK`, `WORKDAY_ALREADY_CLOSED`, `BREAK_ALREADY_OPEN`, `BREAK_NOT_OPEN`, `CORRECTION_ALREADY_PENDING`, `CORRECTION_ALREADY_RESOLVED`, `CONCURRENT_MODIFICATION`.

## 3. Eventos de dominio

Inmutables (records Java), sin Spring/JPA, solo datos necesarios, se generan dentro del agregado y el caso de uso los recoge tras persistir:

`TenantRegistered`, `EmployeeCreated`, `EmployeeDeactivated`, `WorkdayStarted`, `BreakStarted`, `BreakEnded`, `WorkdayClosed`, `CorrectionRequested`, `CorrectionApproved`, `CorrectionRejected`.

Campos comunes: `eventId` (UUID), `occurredAt` (Instant), `tenantId`, `aggregateId` + datos propios mínimos.

## 4. Eventos de integración (iteración 7)

Los eventos de dominio relevantes se mapean a eventos de integración versionados que se escriben en la tabla `outbox_message` **en la misma transacción** que el cambio de negocio.

Tipos: `tenant.registered.v1`, `identity.employee-created.v1`, `identity.employee-deactivated.v1`, `time-tracking.workday-started.v1`, `time-tracking.workday-closed.v1`, `corrections.correction-requested.v1`, `corrections.correction-approved.v1`, `corrections.correction-rejected.v1`.

Envelope:

```json
{
  "eventId": "uuid",
  "eventType": "time-tracking.workday-closed.v1",
  "eventVersion": 1,
  "occurredAt": "ISO-8601 UTC",
  "tenantId": "uuid",
  "aggregateId": "uuid",
  "payload": {}
}
```

Nunca publicar entidades JPA ni modelos internos. Contratos documentados en `docs/integration/event-catalog.md`. Entrega at-least-once; consumidores idempotentes.

## 5. Gestión temporal

Persistir `Instant` UTC. Límites de día en la zona IANA del tenant. Incluir tests de cambio horario estacional (DST) donde se calculen límites de día o resúmenes.
