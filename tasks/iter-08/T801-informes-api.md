# T801 — Informes: casos de uso, API y CSV

- Iteración: 8 · Depende de: T403 · Contexto: CONTEXT-API §2 (informes), CONTEXT-DOMINIO §5

## Objetivo
Informes de tiempo trabajado por empleado y por tenant, con exportación CSV.

## Detalle
1. `reporting.application` (solo lectura; puede consultar por puerto de query propio sin pasar por agregados):
   - `GenerateEmployeeTimeSummary(employeeId, from, to)`: por día (límites de día en la **zona del tenant**): tiempo trabajado (jornada menos pausas), tiempo de pausa, nº jornadas, jornadas ajustadas. EMPLOYEE puede pedir el suyo; TENANT_ADMIN el de cualquiera de su tenant (otro empleado pidiendo el de otro → 403/404 según regla de no revelar).
   - `GenerateTenantTimeSummary(from, to)`: agregado por empleado en el rango. Solo TENANT_ADMIN.
   - `ExportTimeSummaryCsv(from, to)`: mismo dato que el summary de tenant en `text/csv` (cabecera, separador coma, UTF-8 con BOM opcional documentado, campos escapados).
2. Jornadas abiertas en el rango: excluir del total y marcar contador `openWorkdays` (decisión fijada; documentar en la respuesta).
3. `interfaces.rest`: los 3 endpoints de CONTEXT-API §2 "Informes". Validar rango (from ≤ to, máx. 366 días → 400).

## Pruebas
- Unitarias: cálculo de duraciones con pausas; jornada que cruza medianoche en la zona del tenant; **cambio horario estacional** (día de 23 h y de 25 h en Europe/Madrid); escapado CSV.
- Integración: endpoints con roles correctos; cross-tenant en suite T303; CSV descargable con content-type correcto.

## Criterios de aceptación
- `mvn verify` verde; OpenAPI actualizado; tests DST incluidos.

## Ficheros previstos
`reporting/**`, tests, `docs/api/`.
