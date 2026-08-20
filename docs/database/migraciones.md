# Historial de migraciones

Las migraciones viven en `backend/src/main/resources/db/migration` y las aplica
**Flyway** al arrancar la aplicación. El nombre sigue el formato
`V<N>__descripcion_en_minusculas.sql`.

Leídas en orden, cuentan cómo creció el modelo: identidad, fichaje,
correcciones, traza, ciclo de vida del tenant, seguridad de la cuenta,
planificación, ausencias y, al final, los ajustes de rendimiento medidos sobre
datos reales.

## Reglas

- **Una migración aplicada no se edita jamás.** Flyway valida el checksum de
  cada fichero contra `flyway_schema_history`; cambiar uno ya aplicado impide
  arrancar. Todo arreglo es una migración nueva.
- **Toda tabla de negocio lleva `tenant_id`**, y la unicidad relativa a la
  organización se declara como `UNIQUE (tenant_id, …)`.
- **Claves primarias `UUID` generadas por la aplicación**, nunca `SERIAL` ni
  `IDENTITY`.
- **Columnas temporales `TIMESTAMPTZ`** salvo cuando el concepto es una fecha de
  calendario, que va en `DATE`.
- **Los `DROP` y `TRUNCATE` destructivos exigen justificación** y un ADR si
  afectan a datos de producción.
- **El backfill va en la misma migración** que el cambio de esquema que lo hace
  necesario, y se explica en un comentario de cabecera (véanse `V10`, `V20`,
  `V23`).

Procedimiento completo en `.skills/create-database-migration/SKILL.md`.

## Cómo añadir una

1. Tomar el siguiente número libre y crear `V<N>__descripcion.sql`.
2. Escribir el DDL siguiendo las convenciones del
   [README](README.md#convenciones-del-esquema), con un comentario de cabecera
   que explique **por qué** el modelo es así, no qué hace el SQL.
3. Ajustar las entidades JPA. Como `ddl-auto` es `validate`, cualquier
   divergencia impide arrancar.
4. `mvn verify`: los tests de integración levantan PostgreSQL con Testcontainers
   y aplican todas las migraciones desde cero.
5. Actualizar el [diccionario de datos](diccionario-de-datos.md) y, si la
   migración añade o cambia una relación, [relaciones.md](relaciones.md).

## Historial

Las versiones **13 y 15 no existen**: son huecos de numeración de trabajo que
nunca se llegó a mezclar. Flyway los admite sin problema, y renumerar las
posteriores rompería el checksum de las bases ya migradas.

| Versión | Qué introduce |
| --- | --- |
| `V1__baseline` | Línea base vacía. El scaffolding no creó ninguna tabla |
| `V2__identity` | `tenant`, `app_user`, `user_role`, `refresh_token` |
| `V3__global_unique_user_email` | El correo pasa de único por tenant a **único global** ([ADR-0008](../adr/ADR-0008-email-global-unico-para-autenticacion.md)) |
| `V4__timetracking` | `workday` y `break_entry`, con los índices únicos parciales de jornada activa y pausa abierta |
| `V5__corrections` | `correction_request` con `proposed_changes JSONB` y el único parcial de solicitud pendiente |
| `V6__audit` | `audit_event` y sus tres índices de consulta |
| `V7__correction_request_version` | Bloqueo optimista en `correction_request` |
| `V8__outbox` | `outbox_message`: Transactional Outbox ([ADR-0005](../adr/ADR-0005-transactional-outbox-sin-broker.md)) |
| `V9__processed_event` | `processed_event` para la deduplicación del consumidor de ejemplo |
| `V10__tenant_lifecycle` | Ciclo de vida del tenant: marcas de tiempo, motivo de suspensión y `CHECK` de estado. **Backfill**: `INACTIVE` → `SUSPENDED` |
| `V11__platform_tenant` | Inserta el tenant de sistema `…0001` para los `PLATFORM_ADMIN` |
| `V12__tenant_registration` | `tenant_registration`, separada de `tenant` ([ADR-0016](../adr/ADR-0016-solicitud-de-alta-separada-del-tenant.md)) |
| `V14__account_lockout` | `account_lockout` como tabla propia ([ADR-0014](../adr/ADR-0014-bloqueo-de-cuenta-y-rate-limiting-por-patron.md)) |
| `V16__calendar` | Calendarios laborales: `work_calendar` y sus reglas, festivos, jornadas especiales y asignaciones ([ADR-0017](../adr/ADR-0017-calendarios-laborales-y-resolucion-por-ambito.md)) |
| `V17__sessions` | `user_session` y enlace `refresh_token.session_id`, que hace revocable la sesión |
| `V18__password_reset` | `password_reset_token` |
| `V19__workday_hourly_rules_and_evaluation` | `hourly_rules` y `workday_evaluation` |
| `V20__workday_rounding_and_tolerance` | Redondeo y tolerancia en las reglas; `effective_worked_minutes` y `deviation_minutes` en la evaluación. **Backfill** de las evaluaciones existentes |
| `V21__absence` | `absence_type` y `absence_request` |
| `V22__shift` | `shift_template` y `shift_assignment`, con turnos que cruzan medianoche |
| `V23__processed_event_per_consumer` | La deduplicación pasa a ser por `(event_id, consumer)`. **Backfill** al consumidor de demostración |
| `V24__notification` | `notification`, con el aviso en la aplicación y su entrega por correo en la misma fila |
| `V25__reporting_and_listing_indexes` | Dos índices deducidos de planes de ejecución reales sobre 100.000 jornadas: `break_entry(workday_id)` y `correction_request(tenant_id, created_at DESC)` |
| `V26__notification_channel_and_action` | `email_required` y `action_path`; el índice parcial de la cola refleja el nuevo filtro ([ADR-0018](../adr/ADR-0018-destinatarios-por-rol-y-politica-de-canal-de-notificacion.md)) |
| `V27__queue_discard` | Estado `DISCARDED` en notificaciones y outbox, con índices parciales de fallidos ([ADR-0020](../adr/ADR-0020-mantenimiento-manual-de-colas-fallidas.md)) |

## Dos migraciones que conviene leer enteras

Sirven de modelo del nivel de justificación que se espera en el comentario de
cabecera:

- **`V25__reporting_and_listing_indexes`** documenta el plan de ejecución antes
  y después de cada índice, y explica por qué el resto de claves ajenas se
  quedan sin índice propio a propósito. Análisis completo en
  [`docs/reviews/performance-review.md`](../reviews/performance-review.md).
- **`V23__processed_event_per_consumer`** explica por qué la clave primaria
  original dejó de servir al aparecer un segundo consumidor, y cómo se rellenan
  las filas antiguas sin perder la deduplicación ya hecha.
