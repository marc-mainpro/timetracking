## T801 — Informes: casos de uso, API y CSV

### Cambios

- `reporting.domain`:
  - `ReportDateRange`: value object que valida `from <= to` y rango máximo de 366 días (`IllegalArgumentException` -> `400`).
  - `WorkdayReportEntry` / `BreakInterval`: proyección mínima de una jornada para el cálculo de informes (no es el agregado `Workday`).
  - `EmployeeDaySummary` / `TenantEmployeeSummary`: resultados del cálculo (desglose diario por empleado; agregado por empleado para tenant).
  - `WorkdaySummaryQueryPort`: puerto de query propio (ver "Decisión arquitectónica" abajo).
  - `TimeSummaryCalculator`: servicio de dominio puro (sin Spring/JPA) que reparte cada jornada/pausa entre los días naturales de la zona del tenant (`ZoneId`), usando `LocalDate.atStartOfDay(zone)` en cada límite de día para que el reparto sea correcto en días de cambio de hora.
- `reporting.application`:
  - `GenerateEmployeeTimeSummaryUseCase`: resuelve tenant/usuario desde `TenantContext`; `EMPLOYEE` solo su propio `employeeId`, `TENANT_ADMIN` cualquiera de su tenant; empleado ajeno o de otro tenant -> `404` (no revelar existencia); carga el `Tenant` para obtener `timezone()`.
  - `GenerateTenantTimeSummaryUseCase`: agregado por empleado en el rango, acotado al tenant actual.
  - `ExportTimeSummaryCsvUseCase` + `TimeSummaryCsvWriter`: reutiliza `GenerateTenantTimeSummaryUseCase` (mismo dato que `tenant/summary`, CONTEXT-API §2) y solo cambia el formato de salida a CSV (cabecera, separador coma, `\r\n`, escapado RFC 4180, UTF-8 sin BOM documentado).
- `reporting.infrastructure.persistence`:
  - `ReportWorkdayJpaRepository` / `ReportBreakJpaRepository`: dos consultas JPQL de proyección (`select new ...Row(...)`) directamente sobre `WorkdayJpaEntity`/`BreakEntryJpaEntity`.
  - `WorkdaySummaryQueryPortAdapter`: combina ambas consultas en memoria (agrupa pausas cerradas por `workdayId`) para construir `WorkdayReportEntry`.
- `reporting.interfaces.rest`:
  - `ReportController`: los 3 endpoints de CONTEXT-API §2 (`GET /api/v1/reports/employees/{employeeId}/summary`, `GET /api/v1/reports/tenant/summary`, `GET /api/v1/reports/tenant/export.csv`).
  - `ReportRestMapper`, `EmployeeDaySummaryResponse`, `TenantEmployeeSummaryResponse`.
- `LayeredArchitectureTest`: añadidas las excepciones puntuales de `ReportRestMapper` -> `EmployeeDaySummary`/`TenantEmployeeSummary` (mismo criterio que `WorkdayRestMapper`/`CorrectionRestMapper`, documentado en el Javadoc de la clase).
- `CrossTenantSecurityIntegrationTest`: 2 tests nuevos (admin de A no puede leer el resumen de un empleado de B; `tenant/summary` de A solo agrega jornadas de A).
- `docs/api/README.md` y `tasks/STATUS.md` actualizados.

### Decisión arquitectónica: `WorkdaySummaryQueryPort` no reutiliza `WorkdayRepository`

Documentada en el Javadoc de `WorkdaySummaryQueryPort` (mismo criterio que el `LayeredArchitectureTest` de T403 para los mappers REST: excepción puntual y documentada, no relajación general de la regla).

`WorkdayRepository` (dominio de `timetracking`) reconstruye el agregado `Workday` completo (con `List<Object> domainEvents`, invariantes de transición, breaks vía `@OneToMany EAGER`) y pagina pensando en listados interactivos. Reutilizarlo para un informe obligaría a iterar página a página sobre potencialmente miles de jornadas por tenant/rango solo para sumar duraciones. En su lugar, `WorkdaySummaryQueryPort` se implementa en `reporting.infrastructure.persistence` con dos consultas JPQL de **proyección** (`select new ...Row(...)`) que solo traen las columnas necesarias (id, employeeId, status, startedAt, endedAt, y pausas cerradas), sin construir el agregado de dominio `Workday` ni sus eventos. La implementación reutiliza deliberadamente las entidades JPA `WorkdayJpaEntity`/`BreakEntryJpaEntity` ya mapeadas en `timetracking.infrastructure.persistence` (fuente única del mapeo de esquema) en vez de duplicar el mapeo, algo permitido por `ModuleCyclesTest`/`LayeredArchitectureTest` porque es una dependencia infraestructura-a-infraestructura entre módulos (no cruza la regla de capas, que solo restringe accesos *hacia* infraestructura desde otras capas).

### Otras decisiones documentadas

- **Jornadas abiertas**: se excluyen de `worked`/`paused` (no hay forma fiable de saber cuánto trabajará aún el empleado) y se cuentan en `openWorkdays`, tanto en el desglose diario como en el agregado de tenant.
- **Asignación de una jornada a un día** (para `workdayCount`/`adjustedWorkdayCount`/`openWorkdays`): se cuenta en el día local (zona del tenant) en el que la jornada **empieza**, aunque su tiempo trabajado se reparta entre varios días si cruza medianoche local.
- **Agregado de tenant sin desglose diario**: `TenantEmployeeSummary` no depende de la zona horaria del tenant porque la suma de los segmentos diarios de una jornada es igual a la duración completa del intervalo, independientemente de dónde caigan los límites de día; por eso `GenerateTenantTimeSummaryUseCase` no necesita cargar el `Tenant`.
- **CSV**: columnas `employeeId,workedSeconds,pausedSeconds,workdayCount,adjustedWorkdayCount,openWorkdays` (segundos enteros en vez de `PT8H30M`, para que cualquier hoja de cálculo/script lo importe sin ambigüedad). UTF-8 sin BOM (decisión explícita, documentada en el Javadoc de `TimeSummaryCsvWriter`): los consumidores objetivo (import programático, Excel moderno) leen UTF-8 sin BOM correctamente.
- **`ExportTimeSummaryCsv` como caso de uso independiente pero sin duplicar la consulta**: en vez de repetir el acceso a `WorkdaySummaryQueryPort`, `ExportTimeSummaryCsvUseCase` delega en `GenerateTenantTimeSummaryUseCase` y solo aplica un formateador CSV puro (`TimeSummaryCsvWriter`, sin Spring), garantizando "mismo dato" entre `tenant/summary` y `tenant/export.csv` por construcción, no por duplicación de lógica.
- **`from`/`to` obligatorios**: a diferencia de los listados de jornadas/correcciones (donde son filtros opcionales), en informes son obligatorios porque el límite de 366 días necesita ambos extremos.

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 219, Failures: 0, Errors: 0, Skipped: 0`.

Cobertura nueva específica:

- `TimeSummaryCalculatorTest` (dominio puro): duración con pausas, jornada que cruza medianoche local, **día de 23h y día de 25h en `Europe/Madrid`** (cambios de hora de primavera/otoño 2026), jornadas abiertas excluidas de totales pero contadas en `openWorkdays`, jornadas ajustadas, y que el agregado de tenant no depende de los límites de día.
- `ReportDateRangeTest`: rango válido, `to < from`, exactamente 366 días (ok), 367 días (rechazado), nulls.
- `TimeSummaryCsvWriterTest`: cabecera + filas, lista vacía, escapado de coma/comilla/salto de línea, `null` -> cadena vacía.
- `GenerateEmployeeTimeSummaryUseCaseTest` / `GenerateTenantTimeSummaryUseCaseTest` / `ExportTimeSummaryCsvUseCaseTest`: unitarias con mocks (rol EMPLOYEE/TENANT_ADMIN, empleado ajeno -> 404, empleado inexistente -> 404, rango inválido -> `IllegalArgumentException`).
- `ReportControllerIntegrationTest` (MockMvc + Testcontainers): resumen propio del empleado, empleado no puede leer el de otro, admin puede leer cualquiera de su tenant, jornadas abiertas excluidas del total, `tenant/summary` solo para `TENANT_ADMIN` (403 para `EMPLOYEE`), agregación por empleado, CSV descargable con `Content-Type: text/csv` y contenido correcto, `EMPLOYEE` no puede exportar CSV, rango invertido -> `400`, rango > 366 días -> `400`.
- `CrossTenantSecurityIntegrationTest`: 2 tests nuevos — admin de tenant A no puede leer el resumen de un empleado de tenant B (`404`); `tenant/summary` de A solo agrega jornadas de A aunque B también tenga jornadas en el mismo rango.

### Cobertura

- JaCoCo verde en los gates del proyecto: `check-domain-coverage` y `check-application-coverage` -> "All coverage checks have been met." (dominio ≥90%, aplicación ≥80%).

### Seguridad

- Todos los casos de uso resuelven tenant/usuario exclusivamente desde `TenantContext`; el `employeeId` del path solo se usa como filtro objetivo, nunca como fuente de tenant.
- `GET .../employees/{employeeId}/summary`: `EMPLOYEE` solo su propio id; `TENANT_ADMIN` cualquiera de su tenant. Empleado de otro tenant, o empleado ajeno pedido por un `EMPLOYEE`, responde `404` (no `403`), siguiendo el mismo patrón de "no revelar existencia" que jornadas/correcciones.
- `GET .../tenant/summary` y `.../tenant/export.csv`: `@PreAuthorize("hasRole('TENANT_ADMIN')")`, y la query siempre está acotada a `tenantContext.currentTenantId()`.

### Documentación actualizada

- `docs/api/README.md`: 3 endpoints nuevos en la tabla + descripción de comportamiento (límites de día en zona del tenant, jornadas abiertas, formato CSV).
- `tasks/STATUS.md`: fila T801 -> `hecha`.

### ADR

- No fue necesaria una ADR nueva. La única decisión arquitectónica no trivial (`WorkdaySummaryQueryPort` como puerto de query dedicado en vez de reutilizar `WorkdayRepository`) queda documentada en el Javadoc del propio puerto y en este report, siguiendo el mismo criterio que la excepción de `LayeredArchitectureTest` fijada en T403.

### Riesgos detectados

1. `ReportWorkdayJpaRepository`/`ReportBreakJpaRepository` dependen directamente de `WorkdayJpaEntity`/`BreakEntryJpaEntity` (públicas) del módulo `timetracking.infrastructure.persistence`. Es una dependencia infraestructura-a-infraestructura entre módulos, permitida por ArchUnit (no cruza la regla de capas) y sin ciclos (`timetracking` no depende de `reporting`), pero acopla el esquema de columnas de ambos módulos: un cambio de nombre de columna en `V4__timetracking.sql`/la entidad JPA afectaría también a `reporting` sin que ArchUnit lo señale como violación de capas (sí lo señalaría la compilación, al romper las consultas JPQL).
2. El desglose diario cuenta cada jornada (`workdayCount`/`adjustedWorkdayCount`/`openWorkdays`) en su día de inicio en la zona del tenant, no en cada día que toca; es una decisión de negocio razonable y documentada, pero no está explicitada en el enunciado original de T801 y podría requerir ajuste si el frontend (T802) espera otro criterio.
3. El CSV no incluye BOM UTF-8; documentado como decisión explícita (CONTEXT-API §2 lo deja opcional), pero si en T802/T1002 aparece un caso real de Excel de Windows que no reconoce UTF-8 sin BOM, habría que revisarlo.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T801. El siguiente bloque natural es `T802` (frontend de informes), que puede consumir directamente los 3 endpoints documentados.
