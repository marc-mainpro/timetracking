## T501 — Gestión de empleados: casos de uso + API admin

### Cambios

- Se amplió el dominio `User` con actualización de perfil y helper `hasRole`.
- Se añadió la excepción de negocio `LAST_ADMIN`.
- `UserRepository` ahora soporta:
  - listado paginado por tenant y status
  - conteo de admins activos del tenant
  - conteo de admins activos excluyendo un usuario
- Se implementaron los casos de uso admin en `identity.application`:
  - `CreateEmployeeUseCase`
  - `UpdateEmployeeUseCase`
  - `ActivateEmployeeUseCase`
  - `DeactivateEmployeeUseCase`
  - `AssignRoleUseCase`
  - `ListEmployeesUseCase`
  - `GetEmployeeUseCase`
- `DeactivateEmployeeUseCase` revoca refresh tokens del empleado desactivado.
- Se expuso `EmployeeController` con los 7 endpoints administrativos de empleados.
- Se añadieron DTOs REST y mapper de respuesta para empleados.
- Se amplió la suite de seguridad/auth y cross-tenant para cubrir:
  - refresh inválido tras desactivación
  - admin A no gestiona empleados de B -> `404`

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 152, Failures: 0, Errors: 0, Skipped: 0`.

Cobertura nueva específica:

- unitarias de `CreateEmployee`, `AssignRole`, `DeactivateEmployee`
- integración HTTP CRUD completo de empleados por admin
- `EMPLOYEE` -> `403` en endpoints admin
- `LAST_ADMIN` -> `409`
- empleado desactivado no puede refrescar sesión
- cross-tenant: admin A no obtiene empleado de B

### Cobertura

- JaCoCo sigue en verde para los gates del proyecto.
- El backend de gestión de empleados queda cubierto por unitarias e integración real.

### Seguridad

- Todos los endpoints de empleados requieren `TENANT_ADMIN`.
- Operan solo sobre el tenant del contexto autenticado.
- El sistema impide dejar al tenant sin ningún `TENANT_ADMIN` activo.
- Desactivar un empleado invalida sus refresh tokens y el filtro de estado sigue bloqueando access tokens ya emitidos.

### Documentación actualizada

- `docs/api/README.md`

### ADR

- No fue necesario crear ADR nueva.

### Riesgos detectados

1. La especificación histórica hablaba de duplicado de email dentro del tenant, pero el backend vigente mantiene email globalmente único por `ADR-0008`; T501 se alineó con la decisión vigente para no reabrir la ambigüedad de login.
2. El mapper REST de empleados requirió excepción puntual en `LayeredArchitectureTest`, del mismo estilo que el mapper REST de jornadas, para traducir dominio a DTO en el borde HTTP.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T501.
- El siguiente bloque natural es `T404` y `T502` en frontend.
