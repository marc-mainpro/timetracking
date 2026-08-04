# Reglas de negocio

Fuente de verdad: `tasks/_context/CONTEXT-DOMINIO.md`. Cada regla debe tener
un test unitario asociado.

## Tenant / User

- Nombre de tenant obligatorio; timezone IANA válida.
- Un tenant inactivo no puede operar (ninguna operación de negocio).
- Email único **globalmente** (ADR-0008) para eliminar ambigüedad en el login.
- Un usuario inactivo no se autentica.
- Un usuario pertenece a un único tenant.

## Workday

1. Solo una jornada abierta (`OPEN` u `ON_BREAK`) por empleado.
2. No iniciar pausa sin jornada activa (`OPEN`).
3. No iniciar segunda pausa (ya `ON_BREAK`).
4. No cerrar jornada con pausa abierta.
5. No cerrar jornada ya cerrada (`CLOSED`/`ADJUSTED`).
6. Hora de fichaje = hora del servidor (nunca del cliente).
7. Cambios históricos SOLO vía corrección aprobada (estado pasa a
   `ADJUSTED`).
8. La unicidad de jornada abierta por empleado se valida fuera del agregado
   (`WorkdayRepository.findActiveByEmployee(...)` + constraint de BD).

## BreakEntry

- Pertenece a una jornada.
- `endedAt >= startedAt`.
- Solo una pausa abierta (`endedAt IS NULL`) por jornada.

## CorrectionRequest

- Solo una solicitud `PENDING` por jornada y usuario.
- Una solicitud resuelta no se re-resuelve (→ 409).
- Toda aprobación genera un registro de auditoría y aplica los cambios a la
  jornada de forma controlada (la jornada pasa a `ADJUSTED`).

## WorkCalendar

- El nombre es único dentro del tenant.
- La zona horaria debe ser una zona IANA válida.
- `validTo`, si existe, no puede ser anterior a `validFrom`; ambos extremos son
  inclusivos y `validTo` nulo significa vigencia indefinida.
- Un día de la semana no puede tener dos reglas; un día laborable exige minutos
  esperados mayores que 0 y uno no laborable exige exactamente 0.
- Una misma fecha no puede aparecer dos veces, ni como festivo y jornada especial
  a la vez: con las dos presentes la precedencia dejaría de ser explicable.
- Un calendario archivado no admite edición, ni un segundo archivado, ni nuevas
  asignaciones.
- La jornada esperada de un día **no** cambia porque ese día civil dure 23 o 25
  horas por el cambio de horario estacional.

## CalendarAssignment

- Ámbito `TENANT` sin destinatario; `TEAM` y `EMPLOYEE` con destinatario
  obligatorio (→ 400 si no cuadra).
- Una única asignación por `(tenant, ámbito, destinatario)` (→ 409), respaldada
  por índices únicos parciales en la V16.
- **Resolución del calendario efectivo (contrato reutilizable):** prevalece la
  asignación más específica —empleado > equipo > tenant— entre las que apunten al
  empleado y cuyo calendario esté activo y vigente esa fecha. Un calendario
  archivado o caducado no bloquea su ámbito: se cae al siguiente menos
  específico. Que ningún calendario aplique es un resultado legítimo, no un
  error. La implementa `EffectiveCalendarResolver` y **no debe reimplementarse**
  en otros módulos (ADR-0017).

## Excepciones de dominio → `errorCode`

`TENANT_INACTIVE`, `USER_INACTIVE`, `EMAIL_ALREADY_IN_USE`,
`INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`, `REFRESH_TOKEN_REUSED`,
`WORKDAY_ALREADY_OPEN`, `WORKDAY_NOT_OPEN`, `WORKDAY_OPEN_BREAK`,
`WORKDAY_ALREADY_CLOSED`, `BREAK_ALREADY_OPEN`, `BREAK_NOT_OPEN`,
`CORRECTION_ALREADY_PENDING`, `CORRECTION_ALREADY_RESOLVED`,
`CONCURRENT_MODIFICATION`, `CALENDAR_ARCHIVED`,
`CALENDAR_NAME_ALREADY_EXISTS`, `CALENDAR_DUPLICATE_DAY_RULE`,
`CALENDAR_DUPLICATE_DATE`, `CALENDAR_ASSIGNMENT_ALREADY_EXISTS`.

## Gestión temporal

Se persiste `Instant` en UTC. Los límites de día se calculan en la zona IANA
del tenant. Todo cálculo de límites de día o resúmenes debe incluir tests de
cambio horario estacional (DST).
