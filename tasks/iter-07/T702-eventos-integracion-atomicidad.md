# T702 — Eventos de integración + escritura atómica en Outbox

- Iteración: 7 · Depende de: T701 · Contexto: CONTEXT-DOMINIO §3-4

## Objetivo
Transformar eventos de dominio en eventos de integración versionados y persistirlos en `outbox_message` **en la misma transacción** que el cambio de negocio.

## Detalle
1. Definir `IntegrationEvent` (envelope de CONTEXT-DOMINIO §4) y los payloads para: `tenant.registered.v1`, `identity.employee-created.v1`, `identity.employee-deactivated.v1`, `time-tracking.workday-started.v1`, `time-tracking.workday-closed.v1`, `corrections.correction-requested.v1`, `corrections.correction-approved.v1`, `corrections.correction-rejected.v1`. Payloads mínimos (ids, instantes, estado) — NUNCA entidades JPA ni agregados serializados.
2. Mapper por módulo `DomainEvent → IntegrationEvent` (no todo evento de dominio produce integración; `BreakStarted/BreakEnded` no se publican en MVP — documentarlo).
3. Sustituir la implementación provisional de `DomainEventPublisher` (T203): ahora escribe vía `OutboxWriter` DENTRO de la transacción del caso de uso. Verificar que todos los casos de uso emisores participan en transacción.
4. Serialización JSON con Jackson (ObjectMapper dedicado, fechas ISO-8601 UTC).

## Pruebas
- Unitarias: mapeos dominio→integración (envelope completo, versión, payload mínimo).
- Integración (críticas): commit de negocio ⇒ fila PENDING en outbox en la misma transacción; **rollback de negocio ⇒ ninguna fila outbox** (forzar excepción tras el caso de uso); cerrar jornada crea `time-tracking.workday-closed.v1` con payload correcto.

## Criterios de aceptación
- `mvn verify` verde; cobertura de mapeos; sin dependencia de módulos de negocio hacia infraestructura outbox (solo el puerto).

## Ficheros previstos
`outbox/application/**`, mappers en cada módulo (`*/application/integration/`), tests.
