# Agregados

Fuente de verdad: `tasks/_context/CONTEXT-DOMINIO.md` §1.

Este documento describe el modelo desde el **dominio**. Su reflejo en la base de
datos —tablas, columnas, índices y claves ajenas— está en
[`docs/database/`](../database/README.md).

## Tenant

Campos: `id`, `name`, `status`, `timezone`, `createdAt`, `updatedAt`,
`activatedAt`, `suspendedAt`, `archivedAt`, `suspensionReason`.

Estados: `PENDING`, `ACTIVE`, `SUSPENDED`, `ARCHIVED`. **Solo `ACTIVE` permite
operar**; el resto de módulos lo comprueban con `isActive()`.

Transiciones válidas:

- `PENDING → ACTIVE` (activate)
- `ACTIVE → SUSPENDED` (suspend, con motivo)
- `SUSPENDED → ACTIVE` (reactivate)
- `ACTIVE → ARCHIVED` y `SUSPENDED → ARCHIVED` (archive)

`ARCHIVED` es terminal. Un tenant nace `ACTIVE` por creación directa
(`register`) o `PENDING` por registro público controlado
(`requestRegistration`): el alta pública nunca crea una organización operativa
sin decisión de un `PLATFORM_ADMIN`.

Genera los eventos de dominio `TenantRegistered`, `TenantActivated`,
`TenantSuspended`, `TenantReactivated` y `TenantArchived`.

## User

Campos: `id`, `tenantId`, `email`, `passwordHash`, `firstName`, `lastName`,
`status` (`ACTIVE`|`INACTIVE`), `roles`, `createdAt`, `updatedAt`.

Regla clave: `email` es globalmente único (ADR-0008) para permitir
autenticación por `email + password` sin ambigüedad entre tenants.

## Workday (agregado raíz; contiene BreakEntry)

Campos: `id`, `tenantId`, `employeeId`, `status`, `startedAt`, `endedAt`,
`version` (optimistic locking), `createdAt`, `updatedAt`.

`Workday` contiene la colección de `BreakEntry` y genera los eventos de
dominio `WorkdayStarted`, `BreakStarted`, `BreakEnded` y `WorkdayClosed`.

Estados: `OPEN`, `ON_BREAK`, `CLOSED`, `ADJUSTED`.

Transiciones válidas:

- `OPEN → ON_BREAK` (startBreak)
- `ON_BREAK → OPEN` (endBreak)
- `OPEN → CLOSED` (end)
- `CLOSED → ADJUSTED` (corrección aprobada)

Cualquier otra transición lanza una excepción de dominio (HTTP 409). En
particular, `ADJUSTED` es terminal: una jornada ya ajustada no se cierra ni
se vuelve a ajustar, y una jornada `ON_BREAK` no se puede cerrar sin terminar
antes la pausa.

## BreakEntry (dentro de Workday)

Campos: `id`, `workdayId`, `startedAt`, `endedAt`.

Regla clave: `endedAt` no puede ser anterior a `startedAt`.

## WorkCalendar (agregado raíz; contiene CalendarDayRule, Holiday y SpecialDay)

Campos: `id`, `tenantId`, `name`, `timezone` (IANA), `validFrom`, `validTo`,
`status`, `version` (optimistic locking), `createdAt`, `updatedAt`.

Estados: `ACTIVE`, `ARCHIVED`. Archivar es el borrado lógico: el calendario deja
de participar en la resolución del calendario efectivo pero no se borra, porque
las jornadas ya calculadas y las asignaciones históricas lo referencian.

Contiene tres colecciones de objetos de valor, todas identificadas por su clave
natural y no por un id artificial:

- `CalendarDayRule` (por `dayOfWeek`): si ese día de la semana es laborable y
  cuántos minutos se esperan. Un día sin regla explícita es no laborable.
- `Holiday` (por `date`): festivo, siempre no laborable.
- `SpecialDay` (por `date`): jornada especial que **sustituye** la jornada
  esperada de esa fecha; con `expectedMinutes = 0` la deja no laborable.

Precedencia al evaluar una fecha (`dayOf`): fuera de vigencia > jornada
especial > festivo > regla semanal.

Genera los eventos de dominio `WorkCalendarCreated`, `WorkCalendarUpdated` y
`WorkCalendarArchived`.

**Fechas locales, no instantes.** La vigencia, los festivos y las jornadas
especiales son `LocalDate`: «el 6 de enero es festivo» es un día del calendario
civil, no un punto de la línea temporal. La conversión ocurre solo en los bordes
con `startOfDay`, `endOfDayExclusive` y `civilDayLength` (ver ADR-0017).

## CalendarAssignment

Campos: `id`, `tenantId`, `calendarId`, `scope`, `targetId`, `createdAt`,
`updatedAt`.

Ámbitos: `TENANT` (sin destinatario), `TEAM` y `EMPLOYEE` (con destinatario
obligatorio). Una única asignación por `(tenant, ámbito, destinatario)`.

No tiene vigencia propia: la dimensión temporal vive en el calendario. Genera
`CalendarAssigned` y `CalendarAssignmentRemoved`.

El `targetId` de ámbito `TEAM` es un identificador **opaco**: el sistema todavía
no gestiona equipos (ADR-0017), así que la pertenencia empleado-equipo la aporta
quien invoca la resolución.

## CorrectionRequest

Campos: `id`, `tenantId`, `workdayId`, `requestedBy`, `reason`,
`proposedChanges` (JSON), `status`, `resolvedBy`, `resolvedAt`,
`resolutionComment`, `version` (optimistic locking), `createdAt`.

Estados: `PENDING`, `APPROVED`, `REJECTED`.

---

Este documento cubre los agregados del núcleo. Los módulos posteriores
—ausencias, turnos, evaluación de jornada, reglas horarias, notificaciones,
sesiones y solicitudes de alta— aún no tienen ficha aquí; su modelo persistido
está descrito en
[`docs/database/diccionario-de-datos.md`](../database/diccionario-de-datos.md).
