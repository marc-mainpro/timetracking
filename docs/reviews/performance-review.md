# Revisión de rendimiento (T160-03)

Fecha: 2026-08-07. Cubre los escenarios que pide el plan: login, fichaje,
listado de jornadas, informes, Outbox y notificaciones.

## Método

No es una prueba de carga: es un análisis de **planes de ejecución** sobre
volumen sintético, que es lo que detecta los problemas que escalan mal. Una
prueba de carga sobre un dataset pequeño habría dado tiempos excelentes y no
habría encontrado nada.

Volumen: **20 tenants × 25 empleados × 200 jornadas = 100.000 jornadas** y otras
tantas pausas, generadas con `scripts/perf/seed-volume.sql`. El reparto entre
varios tenants es deliberado: el fallo que buscábamos es precisamente el que
hace que el coste de un tenant dependa de los datos de los demás.

Medición con `EXPLAIN (ANALYZE, BUFFERS)` antes y después de cada índice.

## Hallazgo 1 — Los informes recorrían las pausas de todos los tenants

`break_entry` no tenía ningún índice utilizable por `workday_id`. El único que
existía, `ux_break_entry_open`, es **parcial** y cubre solo las pausas abiertas
(`WHERE ended_at IS NULL`), mientras que los informes leen justo las cerradas.

Informe de **un empleado y un mes**, que devuelve 30 filas:

| | Plan | Filas leídas | Buffers | Tiempo |
|---|---|---|---|---|
| Antes | `Seq Scan on break_entry` + Hash Join | 100.023 | 941 | 17,7 ms |
| Después | `Nested Loop` + `Index Scan` | 30 | 66 | 0,33 ms |

Lo relevante no es el factor 53×, sino que **el coste deja de crecer con el
tamaño total de la tabla**. Sin el índice, el informe mensual de un tenant se
degrada a medida que otros tenants —con los que no tiene ninguna relación—
acumulan datos.

Corregido en `V25__reporting_and_listing_indexes.sql`.

## Hallazgo 2 — El listado de correcciones tenía el mismo problema

`correction_request` se lista siempre por tenant, ordenado por fecha de creación
descendente y con filtros opcionales de estado y solicitante, pero solo tenía la
clave primaria y un único parcial sobre `(workday_id, requested_by)` para las
pendientes. El listado de un tenant recorría las correcciones de todos los
demás. Índice `(tenant_id, created_at DESC)` añadido en la misma migración.

## Lo que se revisó y NO necesitaba índice

El plan (T100-02) advierte de «evitar duplicación innecesaria», así que se
descartaron explícitamente:

- **Informe de tenant a un año** (4.125 de 100.000 filas): el planificador usa
  `ix_workday_tenant_employee_started_at` como *bitmap index scan* saltándose
  `employee_id`, y resuelve en 1,6 ms. Un índice dedicado `(tenant_id,
  started_at)` no aportaría lo suficiente para justificar su mantenimiento.
- **Ausencias y turnos consultados al cerrar cada jornada**, que es el camino
  más caliente del producto: ya los cubren `ix_absence_request_tenant_employee_date`
  y `ix_shift_assignment_tenant_employee`.
- **Siete claves ajenas sin índice propio** (`workday.employee_id`,
  `absence_request.employee_id`, `shift_assignment.employee_id`,
  `correction_request.requested_by`/`resolved_by`/`workday_id`,
  `tenant_registration.created_tenant_id`). PostgreSQL no las indexa solo, pero
  todas sus consultas van por índices compuestos que empiezan por `tenant_id`, y
  no hay borrado físico de filas padre que obligue a recorrer la tabla hija
  (§10.4 del diseño: se archiva, no se borra). Se documenta la decisión para que
  la próxima revisión no repita el análisis.

Consulta usada para detectarlas, reutilizable en futuras revisiones:

```sql
SELECT c.conrelid::regclass AS tabla, a.attname AS columna_fk
FROM pg_constraint c
JOIN unnest(c.conkey) WITH ORDINALITY k(attnum, ord) ON true
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
WHERE c.contype = 'f'
  AND NOT EXISTS (
    SELECT 1 FROM pg_index i
    WHERE i.indrelid = c.conrelid AND i.indpred IS NULL
      AND (i.indkey::smallint[])[0] = a.attnum)
ORDER BY 1, 2;
```

## Escenarios sin hallazgos

- **Login**: dominado por BCrypt, que es coste deliberado. La búsqueda por email
  usa el único global `uq_app_user_email`.
- **Fichaje**: escritura sobre un agregado localizado por
  `ux_workday_active (tenant_id, employee_id) WHERE status IN ('OPEN','ON_BREAK')`.
- **Listado de jornadas**: paginado y cubierto por
  `ix_workday_tenant_employee_started_at`.
- **Outbox**: la reclamación usa `FOR UPDATE SKIP LOCKED` sobre índice de estado
  y próximo intento, con lotes acotados por configuración.
- **Notificaciones**: listado por `(tenant_id, recipient_user_id, created_at DESC)`
  y cola de envío por índice parcial sobre las pendientes, que son minoría frente
  al histórico.

## Limitaciones de esta revisión

- **No hay prueba de carga con concurrencia.** Se ha analizado el coste por
  consulta, no el comportamiento bajo peticiones simultáneas ni el
  dimensionamiento del pool de conexiones. Queda fuera del alcance de la V2, que
  no contempla alta disponibilidad ni escalado horizontal (RC-008, RC-009).
- Las cifras provienen de un contenedor de desarrollo con la base en caliente
  (`shared hit`, sin lecturas a disco). Sirven para comparar planes entre sí, no
  como referencia de latencia en producción.
