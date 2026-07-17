# T701 — Migración y modelo Outbox

- Iteración: 7 · Depende de: T103 · Contexto: CONTEXT-DOMINIO §4, SDD §14

## Objetivo
Tabla `outbox_message` y modelo/repositorio en infraestructura, aislado del resto de módulos.

## Detalle
1. `V6__outbox.sql` con la tabla EXACTA de la sección 14.2 del SDD (`outbox_message`: id, tenant_id, aggregate_type, aggregate_id, event_type, event_version, payload JSONB, occurred_at, published_at, attempts, next_attempt_at, last_error, status, created_at). Índice para el poller: `(status, next_attempt_at)`.
2. `outbox.infrastructure`: entidad JPA `OutboxMessageEntity` (estados `PENDING|PROCESSING|PUBLISHED|FAILED`), repositorio con query de reclamación por lotes usando **`FOR UPDATE SKIP LOCKED`** (query nativa: seleccionar N PENDING con `next_attempt_at <= now` y marcarlas PROCESSING) y métodos `markPublished`, `markRetry(attempts, nextAttemptAt, lastError)`, `markFailed`, `archivePublishedBefore(instant)`.
3. Puerto `OutboxWriter` en `outbox.application` (o shared): `write(IntegrationEvent)` — lo usará T702 desde las transacciones de negocio. El resto de módulos SOLO conoce este puerto (regla ArchUnit T106.5).

## Pruebas
- Integración: migración; reclamación por lotes con SKIP LOCKED (dos "workers" concurrentes no reclaman el mismo mensaje); transiciones de estado.

## Criterios de aceptación
- `mvn verify` verde; ArchUnit de aislamiento outbox verde.

## Ficheros previstos
`V6__outbox.sql`, `outbox/**`, tests IT.
