## T802 — Frontend: informes básicos

### Cambios

- Nuevo `ReportsService` (`frontend/src/app/features/reports/reports.service.ts`) que consume las tres rutas de T801:
  - `GET /api/v1/reports/employees/{employeeId}/summary?from=&to=`
  - `GET /api/v1/reports/tenant/summary?from=&to=`
  - `GET /api/v1/reports/tenant/export.csv?from=&to=` con `responseType: 'blob'`.
  Los tipos TS (`EmployeeDaySummary`, `TenantEmployeeSummary`) reflejan exactamente los DTOs reales del backend (`EmployeeDaySummaryResponse`, `TenantEmployeeSummaryResponse`): `worked`/`paused` como duración ISO-8601 (`java.time.Duration`), `workdayCount`, `adjustedWorkdayCount`, `openWorkdays`.
- Utilidad `formatIsoDuration` (`duration.util.ts`) para mostrar `PT8H30M` como `08:30` en las tablas.
- **Admin** (`ReportsComponent`, ruta `admin/reports`, guard `authGuard + roleGuard(['TENANT_ADMIN'])`, antes en `reports`): formulario reactivo (`FormBuilder.nonNullable.group`) con selector de rango `from`/`to`, validador de grupo `rangeValidator` (espejo cliente de `from <= to`), tabla de resumen por empleado con las 6 columnas del DTO de tenant, botón "Exportar CSV" que pide el CSV como `Blob` y dispara la descarga vía `URL.createObjectURL` + `<a download>`. Estados: `loading`, "sin datos en el rango seleccionado" cuando el array es vacío, y error 400/`INVALID_ARGUMENT` mostrado en el formulario vía `ErrorMessagesService`.
- **Empleado** (`EmployeeReportComponent`, nueva ruta dedicada `reports` bajo el guard `EMPLOYEE` ya existente): mismo patrón de rango de fechas, usa `AuthService.currentUserId()` (nuevo método, añadido para este caso, que expone `sub` del JWT ya decodificado) para pedir su propio resumen diario sin necesitar más contexto.
- `AuthService.currentUserId()` añadido en `core/services/auth.service.ts` (lee `claims().sub`, que el backend rellena con `user.id()` — ver `JwtAccessTokenGenerator`).
- Navegación (`app.component.html`): enlace admin actualizado a `/admin/reports` ("Informes") y nuevo enlace de empleado `/reports` ("Mis informes").
- `app.routes.ts`: la ruta antigua `reports` (admin) pasa a `admin/reports` (coherente con `admin/employees`/`admin/corrections`); se añade `reports` bajo el guard `EMPLOYEE` para el resumen propio, cargando `employee-report.routes.ts` (`EMPLOYEE_REPORT_ROUTES`).

### Decisión: dónde va el resumen del empleado

Se optó por una **ruta dedicada** (`/reports`, guard `EMPLOYEE` ya existente) en vez de incrustarlo como sub-sección de `employee-dashboard`, para no sobrecargar la pantalla de fichaje (que ya tiene su propio ciclo de refresco cada segundo) y para reutilizar el mismo patrón de layout que el resto de pantallas de rango de fechas (formulario + tabla). Se añadió un enlace de navegación visible ("Mis informes") para que quede descubrible.

### Pruebas

Nuevos specs con `HttpTestingController`, siguiendo el patrón de `admin-employees.component.spec.ts`:

- `reports.component.spec.ts` (admin): carga inicial del resumen de tenant, render de la tabla con los datos reales del backend (incluye formato de duración), estado vacío, validación cliente de rango invertido (no llega a lanzar petición), error 400/`INVALID_ARGUMENT` del backend mostrado en el formulario, y exportación CSV: verifica `responseType: 'blob'`, los parámetros `from`/`to` exactos enviados, y que se invoca `URL.createObjectURL`/`click()`/`URL.revokeObjectURL`.
- `employee-report.component.spec.ts`: carga del resumen propio usando el `employeeId` mockeado vía `AuthService`, render de filas diarias, estado vacío y validación de rango invertido.

### Cobertura

Suite frontend completa: **34 tests**, todos verdes. T802 añade 6 tests en `reports.component.spec.ts` y 4 en `employee-report.component.spec.ts` (sustituyendo el único test placeholder previo de `reports.component.spec.ts`).

### Seguridad

- `/admin/reports` sigue protegida por `authGuard + roleGuard(['TENANT_ADMIN'])`, igual que antes del cambio de ruta.
- `/reports` (empleado) protegida por `authGuard + roleGuard(['EMPLOYEE'])`, reutilizando el guard ya existente para `workdays`/`corrections`.
- El interceptor `authInterceptor` ya existente añade el Bearer automáticamente a la petición `blob` de exportación CSV, sin cambios adicionales.
- No se expone ningún dato de otro tenant/empleado: el `employeeId` del resumen propio se obtiene únicamente del JWT ya validado por el backend (`AuthService.currentUserId()`), nunca de un parámetro editable por el usuario.

### Documentación actualizada

- `tasks/STATUS.md`: fila `T802` pasa a "hecha".

### ADR

- No fue necesaria ninguna ADR nueva; T802 es puramente frontend sobre una API ya diseñada en T801.

### Riesgos detectados

1. El rango de fechas se envía como `T00:00:00Z`/`T23:59:59Z` (UTC), no en la zona horaria del tenant; para tenants en zonas muy alejadas de UTC el límite del día visual en el formulario puede diferir en unas horas del límite real usado por el backend (que sí calcula por zona del tenant en el resumen diario de empleado). No afecta a la corrección del cálculo del backend, solo a qué "día calendario UTC" cubre exactamente el filtro visual.
2. No se verificó manualmente contra un backend local corriendo (solo build + tests automatizados), tal y como se indicó en el encargo.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T802.
- Si se quiere alinear estrictamente el selector de fechas con la zona horaria del tenant (en vez de UTC), sería un ajuste menor a futuro, no bloqueante.
