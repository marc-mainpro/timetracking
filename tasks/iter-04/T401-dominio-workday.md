# T401 — Dominio Workday + BreakEntry con eventos

- Iteración: 4 · Depende de: T202 · Contexto: CONTEXT-DOMINIO §1-3 (imprescindible), CONTEXT-GLOBAL §4

## Objetivo
Agregado `Workday` puro con TODAS las invariantes de CONTEXT-DOMINIO y sus eventos de dominio. Es el corazón del sistema: máxima calidad de tests.

## Detalle
1. `timetracking.domain`: agregado `Workday` (raíz, contiene lista de `BreakEntry`), enum `WorkdayStatus {OPEN, ON_BREAK, CLOSED, ADJUSTED}`.
2. Operaciones: `start(tenantId, employeeId, now)` (factoría), `startBreak(now)`, `endBreak(now)`, `close(now)`, `adjust(changes, now)` (para T602). Cada operación valida su invariante y lanza la `DomainException` con el `errorCode` correspondiente (CONTEXT-DOMINIO §2). El instante `now` siempre llega del puerto `Clock` vía caso de uso — el dominio no llama a `Instant.now()`.
3. Invariante "una jornada abierta por empleado" NO puede validarla el agregado solo: definir en el puerto `WorkdayRepository` `findActiveByEmployee(tenantId, employeeId)` y validar en el caso de uso (T403) + constraint de BD (T402) como red de seguridad.
4. Eventos: `WorkdayStarted`, `BreakStarted`, `BreakEnded`, `WorkdayClosed` generados dentro del agregado (`pullDomainEvents()`).
5. Campo `version` para bloqueo optimista (se mapea en T402).

## Pruebas (unitarias, objetivo ≥90 %)
Toda transición válida e inválida de la tabla de CONTEXT-DOMINIO; pausa sin jornada activa; segunda pausa; cerrar con pausa abierta; doble cierre; `endedAt < startedAt` en pausa; eventos correctos con datos mínimos; inmutabilidad de eventos.

## Fuera de alcance
Persistencia (T402), casos de uso y API (T403).

## Criterios de aceptación
- `mvn verify` verde; ArchUnit verde; cobertura de `timetracking.domain` ≥90 %.

## Ficheros previstos
`timetracking/domain/**` + tests unitarios.
