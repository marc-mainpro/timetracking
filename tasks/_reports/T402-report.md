## T402 — Migración y persistencia de Workday

### Cambios

- Se añadió la migración `V4__timetracking.sql` con tablas:
  - `workday`
  - `break_entry`
- La migración incluye:
  - FK `employee_id -> app_user(id)`
  - índice `(tenant_id, employee_id, started_at)`
  - índice único parcial para una única jornada activa por empleado y tenant
  - índice único parcial para una única pausa abierta por jornada
- Se implementaron entidades JPA separadas del dominio:
  - `WorkdayJpaEntity`
  - `BreakEntryJpaEntity`
- Se implementó `WorkdayMapper` para reconstruir el agregado completo con sus pausas.
- Se implementó `WorkdayRepositoryAdapter` con consultas tenant-aware:
  - `save`
  - `findById(tenantId, id)`
  - `findActiveByEmployee(tenantId, employeeId)`
- `WorkdayJpaEntity` mapea `@Version` sobre `version` para bloqueo optimista.

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 131, Failures: 0, Errors: 0, Skipped: 0`.

Cobertura nueva específica:

- Flyway aplica correctamente `V4`
- se rechaza segunda jornada activa para mismo empleado/tenant
- se rechaza segunda pausa abierta para misma jornada
- persistencia y recarga del agregado completo con pausas
- `findById` con tenant incorrecto devuelve vacío
- `findActiveByEmployee` filtra por tenant y estado activo
- versión obsoleta provoca `ObjectOptimisticLockingFailureException`

### Cobertura

- JaCoCo sigue en verde para los gates de `domain` y `application`.
- La nueva persistencia de `timetracking` queda cubierta por integración real con PostgreSQL/Testcontainers.

### Seguridad

- Todas las consultas de negocio del agregado `Workday` quedan tenant-aware por construcción.
- La red de seguridad de BD complementa la validación de caso de uso para la unicidad de jornada activa.

### Documentación actualizada

- `docs/architecture/components.md`

### ADR

- No fue necesario crear ADR nueva.

### Riesgos detectados

1. El puerto `WorkdayRepository` todavía no expone listados paginados/rango; T403 lo ampliará según necesidades de API.
2. La relación de pausas se carga eager porque el agregado siempre se reconstruye completo; es adecuada para el MVP, pero habrá que vigilar su coste si en el futuro aparecen consultas masivas de jornadas con muchas pausas.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T402.
- La siguiente tarea correcta es `T403`.
