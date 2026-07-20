# Publicador de outbox (T703)

Implementa el lado de entrega del Transactional Outbox (ADR-0005): un job de
polling que reclama mensajes `PENDING`/`PROCESSING` huérfanos de
`outbox_message` (T701) y los entrega a través de un puerto de publicación,
con reintentos, backoff exponencial y métricas.

## Garantía de entrega

**At-least-once**, tal y como fija ADR-0005: un mensaje puede entregarse más
de una vez (p. ej. si el proceso muere justo después de publicar pero antes
de marcar `PUBLISHED`), nunca menos. Los consumidores externos deben ser
idempotentes (deduplicar por `eventId`). No se introduce ningún broker de
mensajería (Kafka/RabbitMQ/...): la única implementación del puerto de
publicación en este MVP es un log estructurado
(`outbox.infrastructure.LoggingIntegrationEventPublisher`). Sustituirla por
un consumidor real en el futuro solo cambia esa clase; el resto del
publicador (reclamación, reintentos, backoff, métricas) no cambia y
requeriría una nueva ADR si implicara un broker.

## Ciclo de vida de un mensaje

```
PENDING --claimBatch--> PROCESSING --publish OK--> PUBLISHED
   ^                        |
   |                        | publish falla, intentos < max
   +--- markRetry (backoff)-+
                             |
                             | publish falla, intentos >= max
                             v
                           FAILED --retry manual--> PENDING (attempts=0)
```

- **Reclamación** (`OutboxMessageRepository#claimBatch`, T701): usa
  `FOR UPDATE SKIP LOCKED` para que múltiples instancias del job (o nodos de
  la aplicación) nunca reclamen el mismo mensaje. Reclama tanto `PENDING`
  listos como `PROCESSING` **huérfanos**: si un worker reclamó un mensaje y
  murió antes de resolverlo (publicar/reintento/fallo), su lease
  (`next_attempt_at`, fijado a `now + outbox.claim-timeout` al reclamar)
  vence y otro worker puede reclamarlo de nuevo.
- **Éxito**: `markPublished` (estado `PUBLISHED`, `published_at = now`).
- **Fallo con intentos restantes**: `markRetry` (vuelve a `PENDING`,
  `attempts += 1`, `next_attempt_at = now + backoff`, `last_error` actualizado).
- **Fallo con intentos agotados** (`attempts >= outbox.max-attempts`):
  `markFailed` (estado `FAILED`, deja constancia del número final de
  intentos y del último error).
- **Reintento manual** (`RetryFailedOutboxMessage`, operación humana): un
  mensaje `FAILED` puede resetearse explícitamente a `PENDING` con
  `attempts = 0` para que el publicador lo vuelva a intentar. No lo invoca
  nunca el flujo automático.

## Backoff

Exponencial con base 1 minuto: `1 min * 2^intentos_previos`, más jitter
aleatorio de hasta un 20% adicional (evita que muchos mensajes fallidos a la
vez converjan en el mismo instante de reintento). Con la configuración por
defecto (`outbox.max-attempts = 8`), el último reintento antes de `FAILED`
puede demorarse hasta ~2 horas desde el primer fallo.

## Casos de uso (`outbox.application`)

- **`PublishPendingOutboxMessages`**: reclama y procesa un lote. Invocable
  directamente (no depende del scheduler), usado tanto por
  `OutboxPublisherJob` como por los tests de integración.
- **`RetryFailedOutboxMessage(id)`**: reintento manual de un mensaje
  `FAILED`. Lanza `ResourceNotFoundException` si el id no existe, o
  `OutboxMessageNotFailedException` si el mensaje no está en `FAILED`.
- **`ArchivePublishedOutboxMessages`**: purga físicamente los mensajes
  `PUBLISHED` con `published_at` anterior a `outbox.archive-retention` (30
  días por defecto). Nunca toca `PENDING`/`PROCESSING`/`FAILED`.

## Jobs programados (`outbox.infrastructure`)

- **`OutboxPublisherJob`**: `@Scheduled(fixedDelayString = "${outbox.poll-interval:PT5S}")`.
- **`OutboxArchiverJob`**: `@Scheduled(cron = "${outbox.archive-cron:0 0 3 * * *}")`
  (03:00 todos los días por defecto).

Ambos están condicionados a `outbox.scheduler-enabled` (por defecto
`true`); se desactivan en el perfil `test`
(`application-test.yml: outbox.scheduler-enabled: false`) para que las
suites de otras funcionalidades no compitan con un publicador en segundo
plano sobre filas de `outbox_message` que no les conciernen. Los tests
propios del publicador invocan los casos de uso directamente (sin sleeps,
con un reloj controlable inyectado) en vez de esperar al cron.

## Configuración (`outbox.*`, `OutboxProperties`)

| Propiedad                    | Por defecto        | Descripción                                                       |
|-------------------------------|--------------------|---------------------------------------------------------------------|
| `outbox.poll-interval`        | `PT5S`             | Intervalo entre lotes del publicador.                                |
| `outbox.batch-size`           | `50`                | Tamaño máximo del lote reclamado por ejecución.                     |
| `outbox.max-attempts`         | `8`                 | Intentos máximos antes de `FAILED`.                                 |
| `outbox.claim-timeout`        | `PT5M`              | Duración del lease de un mensaje `PROCESSING` (recuperación de huérfanos). |
| `outbox.archive-retention`    | `P30D`              | Antigüedad mínima (`published_at`) para purgar un `PUBLISHED`.      |
| `outbox.archive-cron`         | `0 0 3 * * *`       | Cron del job diario de archivado.                                    |
| `outbox.scheduler-enabled`    | `true` (`false` en `test`) | Activa/desactiva ambos jobs programados.                     |

## Métricas (Micrometer / Actuator)

Expuestas en `/actuator/metrics/<nombre>` (`management.endpoints.web.exposure.include: health,metrics`):

- `outbox.messages.published` (contador): publicaciones exitosas.
- `outbox.messages.retried` (contador): fallos que programaron un reintento.
- `outbox.messages.failed` (contador): mensajes marcados `FAILED`.
- `outbox.messages.pending` (gauge): mensajes en `PENDING` + `PROCESSING`
  ahora mismo (backlog).
- `outbox.publish.duration` (timer): duración de cada intento de publicación.

No se expone `/actuator/prometheus` en este MVP (no hay dependencia
`micrometer-registry-prometheus`); el endpoint JSON de Actuator es
suficiente para el alcance actual. Ampliarlo a un registro Prometheus real
es una decisión de despliegue posterior, no de esta ficha.
