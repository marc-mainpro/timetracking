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

## BreakEntry

- Pertenece a una jornada.
- `endedAt >= startedAt`.
- Solo una pausa abierta (`endedAt IS NULL`) por jornada.

## CorrectionRequest

- Solo una solicitud `PENDING` por jornada y usuario.
- Una solicitud resuelta no se re-resuelve (→ 409).
- Toda aprobación genera un registro de auditoría y aplica los cambios a la
  jornada de forma controlada (la jornada pasa a `ADJUSTED`).

## Excepciones de dominio → `errorCode`

`TENANT_INACTIVE`, `USER_INACTIVE`, `EMAIL_ALREADY_IN_USE`,
`INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`, `REFRESH_TOKEN_REUSED`,
`WORKDAY_ALREADY_OPEN`, `WORKDAY_NOT_OPEN`, `WORKDAY_OPEN_BREAK`,
`WORKDAY_ALREADY_CLOSED`, `BREAK_ALREADY_OPEN`, `BREAK_NOT_OPEN`,
`CORRECTION_ALREADY_PENDING`, `CORRECTION_ALREADY_RESOLVED`,
`CONCURRENT_MODIFICATION`.

## Gestión temporal

Se persiste `Instant` en UTC. Los límites de día se calculan en la zona IANA
del tenant. Todo cálculo de límites de día o resúmenes debe incluir tests de
cambio horario estacional (DST).
