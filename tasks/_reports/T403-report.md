## T403 — Casos de uso de fichaje + API de jornadas

### Cambios

- Se implementaron los casos de uso de fichaje y consulta en `timetracking.application`:
  - `StartWorkdayUseCase`
  - `StartBreakUseCase`
  - `EndBreakUseCase`
  - `EndWorkdayUseCase`
  - `GetCurrentWorkdayUseCase`
  - `ListOwnWorkdaysUseCase`
  - `GetWorkdayUseCase`
  - `ListTenantWorkdaysUseCase`
  - `GetTenantWorkdayUseCase`
- `WorkdayRepository` se amplió con listados paginados para empleado y admin.
- Se añadieron los controladores REST:
  - `WorkdayController`
  - `AdminWorkdayController`
- Se añadieron DTOs y mapeo REST para jornadas y pausas.
- `GlobalExceptionHandler` ahora traduce:
  - `ResourceNotFoundException` -> `404`
  - `ObjectOptimisticLockingFailureException` -> `409 CONCURRENT_MODIFICATION`
- Se amplió la suite cross-tenant con jornadas reales y acceso admin/employee sobre `timetracking`.

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 142, Failures: 0, Errors: 0, Skipped: 0`.

Cobertura nueva específica:

- unitarias mínimas de `StartWorkday`, `StartBreak`, `EndBreak`, `EndWorkday`
- flujo HTTP completo `start -> break start -> break end -> end`
- `GET current` -> `404` cuando no hay jornada activa
- `409` en transiciones inválidas (`WORKDAY_ALREADY_OPEN`, `BREAK_ALREADY_OPEN`, `BREAK_NOT_OPEN`, `WORKDAY_OPEN_BREAK`)
- `403` para `EMPLOYEE` en endpoints admin
- cross-tenant: admin de A no obtiene jornadas de B y solo lista jornadas de su tenant

### Cobertura

- JaCoCo sigue en verde para los gates del proyecto.
- El flujo completo de fichaje queda cubierto por integración real con Testcontainers + MockMvc.

### Seguridad

- Todos los endpoints de jornadas resuelven tenant y usuario exclusivamente desde `TenantContext`.
- Endpoints administrativos protegidos con `@PreAuthorize("hasRole('TENANT_ADMIN')")`.
- Recursos de otro tenant responden `404`, no `403`, para no revelar existencia.

### Documentación actualizada

- `docs/api/README.md`

### ADR

- No fue necesario crear ADR nueva.

### Riesgos detectados

1. El mapeo REST de duración trabajada usa el `Clock` del servidor para jornadas abiertas; es correcto para el MVP, pero habrá que revisar cómo presentarlo en frontend/reporting cuando existan zonas horarias y agregados temporales más ricos.
2. La regla de capas requiere una excepción puntual documentada en `LayeredArchitectureTest` para el mapper REST de jornadas, ya que transforma el agregado de dominio a DTO de API.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T403.
- El siguiente bloque correcto es `T404` y `T501`, que pueden solaparse parcialmente una vez fijada la API actual.
