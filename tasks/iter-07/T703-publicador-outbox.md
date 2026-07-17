# T703 — Publicador Outbox: polling, reintentos, backoff, métricas

- Iteración: 7 · Depende de: T702 · Contexto: SDD §14.3-14.4

## Objetivo
Publicador por polling que procesa la outbox con garantía at-least-once. Sin broker (prohibido sin ADR): el destino en MVP es un puerto `IntegrationEventPublisher` con implementación de log estructurado.

## Detalle
1. `OutboxPublisherJob` con `@Scheduled` (intervalo configurable, def. 5 s): reclama lote (T701, def. 50) → por mensaje: publica por el puerto → `markPublished`; si falla → `markRetry` con backoff exponencial (1 min · 2^attempts, jitter) hasta `max_attempts` (def. 8) → `FAILED` con `last_error`.
2. Casos de uso operativos: `PublishPendingOutboxMessages` (invocable además del scheduler, para tests), `RetryFailedOutboxMessage(id)` (FAILED → PENDING, resetea intentos; pensado para operación manual), `ArchivePublishedOutboxMessages` (borra/archiva PUBLISHED > 30 días; job diario).
3. Métricas Micrometer: contadores published/failed/retried, gauge de pendientes, timer de publicación. Exponer por actuator (solo con auth o perfil local — decidir y documentar).
4. Mensajes PROCESSING huérfanos (crash del worker): al reclamar, incluir PROCESSING con `next_attempt_at` vencido (timeout de reclamación, def. 5 min).
5. Configuración por properties con prefijo `outbox.*`.

## Pruebas
- Integración: publicación feliz marca PUBLISHED; fallo del puerto → reintento con backoff creciente; agotados intentos → FAILED; retry manual re-publica; dos instancias del job no duplican publicación (SKIP LOCKED); huérfano PROCESSING se recupera; archivado elimina solo PUBLISHED antiguos.

## Criterios de aceptación
- `mvn verify` verde; métricas visibles; `docs/integration/` describe el publicador y su configuración.

## Ficheros previstos
`outbox/application/**`, `outbox/infrastructure/**` (job, publisher log), config, tests IT.
