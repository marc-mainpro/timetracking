## T401 — Dominio Workday + BreakEntry con eventos

### Cambios

- Se implementó el agregado puro `Workday` en `timetracking.domain` con:
  - estados `OPEN`, `ON_BREAK`, `CLOSED`, `ADJUSTED`
  - `tenantId`, `employeeId`, `startedAt`, `endedAt`, `version`, `createdAt`, `updatedAt`
  - colección interna de `BreakEntry`
- Se implementó `BreakEntry` como entidad hija con identidad propia y validación de cronología.
- Se añadió `WorkdayAdjustment` como objeto mínimo de dominio para permitir la transición a `ADJUSTED` sin bloquear T602.
- Se añadieron el puerto `WorkdayRepository` y las excepciones de dominio:
  - `WORKDAY_NOT_OPEN`
  - `WORKDAY_OPEN_BREAK`
  - `WORKDAY_ALREADY_CLOSED`
  - `BREAK_ALREADY_OPEN`
  - `BREAK_NOT_OPEN`
- Se añadieron eventos de dominio:
  - `WorkdayStarted`
  - `BreakStarted`
  - `BreakEnded`
  - `WorkdayClosed`

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 124, Failures: 0, Errors: 0, Skipped: 0`.

Cobertura funcional nueva:

- factoría `start(...)`
- `startBreak`, `endBreak`, `close`, `adjust`
- transiciones válidas e inválidas
- cronología inválida de pausas y cierre antes del inicio
- eventos correctos con datos mínimos
- `pullDomainEvents()` limpia eventos acumulados
- `reconstitute(...)` no genera eventos

### Cobertura

- JaCoCo se mantiene en verde para los gates del proyecto.
- El nuevo código de `timetracking.domain` queda cubierto por suite unitaria exhaustiva.

### Seguridad

- No se introducen accesos a datos ni dependencias de Spring/JPA en dominio.
- El agregado no confía en tiempo del sistema: `now` llega como parámetro.
- La invariante “una jornada abierta por empleado” queda explícitamente fuera del agregado y se deja para T402/T403 con repositorio + constraint.

### Documentación actualizada

- `docs/domain/agregados.md`
- `docs/domain/reglas-de-negocio.md`

### ADR

- No fue necesario crear ADR nueva.

### Riesgos detectados

1. `adjust(...)` queda modelado con un objeto de ajuste mínimo para no bloquear T602; la forma final del cambio histórico podrá refinarse cuando exista el dominio de correcciones.
2. El agregado no valida por sí solo la unicidad de jornada activa por empleado; eso queda correctamente desplazado a caso de uso + persistencia en T402/T403.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T401.
- La siguiente tarea correcta es `T402`.
