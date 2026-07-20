# T703 — Publicador Outbox: polling, reintentos, backoff, métricas

### Cambios

- **`OutboxMessageRepository` (`outbox.domain`) — ampliada:**
  - `Optional<OutboxMessage> findById(UUID id)`: usada por
    `RetryFailedOutboxMessage` para validar existencia/estado antes de
    resetear.
  - `long countPending()`: cuenta `PENDING + PROCESSING`, usada como gauge
    de backlog.
  - `markFailed(UUID id, String lastError)` → **`markFailed(UUID id, int
    attempts, String lastError)`**: T701 la dejó sin parámetro `attempts`,
    así que al marcar `FAILED` la columna `attempts` se quedaba congelada en
    el valor del último `markRetry` en vez de reflejar el intento final que
    agotó el límite. Se detectó con el propio test de integración de
    backoff (`repeatedFailuresGrowTheBackoffThenFail`, ver "Pruebas") y se
    corrigió ampliando la firma en el puerto, la query nativa
    (`OutboxMessageJpaRepository`) y el adaptador
    (`OutboxMessageRepositoryAdapter`). Se actualizaron también los dos
    tests de T701 que llamaban al método antiguo
    (`OutboxMessageRepositoryAdapterIntegrationTest`).
- **`outbox.application.IntegrationEventPublisher`** (puerto nuevo): unico
  destino del publicador. ADR-0005 prohíbe un broker real en el MVP; la
  única implementación es un log estructurado
  (`outbox.infrastructure.LoggingIntegrationEventPublisher`, `@Component`,
  nunca lanza excepción). Sustituirla en el futuro por un consumidor real
  solo cambiaría esa clase.
- **`outbox.application.OutboxProperties`** (`@ConfigurationProperties(prefix
  = "outbox")`, record con `@DefaultValue`): `pollInterval`, `batchSize`,
  `maxAttempts`, `claimTimeout`, `archiveRetention`, `archiveCron`,
  `schedulerEnabled`. Vive en `outbox.application` (no en
  `outbox.infrastructure`) porque los casos de uso de esa misma capa
  (`PublishPendingOutboxMessages`, `ArchivePublishedOutboxMessages`) la
  necesitan directamente y `LayeredArchitectureTest` prohíbe que
  `application` dependa de `infrastructure`.
- **`outbox.application.OutboxBackoffPolicy`** (paquete-privada, sin
  Spring): backoff exponencial `1 min * 2^intentos_previos` + jitter
  aleatorio de hasta el 20% adicional (evita que muchos mensajes fallidos a
  la vez converjan en el mismo instante de reintento). Recibe un
  `shared.domain.Clock` (nunca `Instant.now()` directo), igual que el resto
  del dominio/aplicación del proyecto.
- **`outbox.application.OutboxMetrics`** (`@Component`, Micrometer):
  contadores `outbox.messages.published/retried/failed`, timer
  `outbox.publish.duration`, gauge `outbox.messages.pending` (delega en
  `OutboxMessageRepository#countPending`). Primer uso de `MeterRegistry` en
  el proyecto (no había precedente); se optó por inyectarlo directamente en
  la capa `application` en vez de crear una abstracción propia, siguiendo la
  misma tolerancia que el proyecto ya tiene con Spring (`@Service`,
  `@Transactional`) en esa capa.
- **`outbox.application.PublishPendingOutboxMessages`** (caso de uso
  central, `@Service`, sin `@Transactional` a nivel de método a propósito):
  `publishBatch()` reclama un lote (`claimBatch`, T701) y por mensaje:
  publica vía `IntegrationEventPublisher` → éxito: `markPublished`; fallo
  con intentos restantes: `markRetry` con el backoff calculado; fallo con
  intentos agotados (`attempts >= outbox.max-attempts`): `markFailed`. Cada
  llamada al repositorio ya abre su propia transacción corta (adaptador de
  T701/T702), así que un mensaje lento/fallido no retiene bloqueadas las
  filas de los demás del lote. Invocable directamente (no depende del
  scheduler), que es justamente lo que permite probarlo sin sleeps.
- **`outbox.application.RetryFailedOutboxMessage`** (caso de uso operativo):
  `retry(UUID id)` — `ResourceNotFoundException` si no existe,
  `outbox.domain.OutboxMessageNotFailedException` (nueva,
  extiende `DomainException`, código `OUTBOX_MESSAGE_NOT_FAILED`) si el
  mensaje no está en `FAILED`; si lo está, `markRetry(id, 0, null, null)`
  lo deja `PENDING` con `attempts = 0`, sin `nextAttemptAt` (elegible de
  inmediato) ni `lastError` previo.
- **`outbox.application.ArchivePublishedOutboxMessages`** (caso de uso
  operativo, pensado como job diario): delega en
  `OutboxMessageRepository#archivePublishedBefore` (ya existía desde T701)
  con el corte calculado como `clock.now() - outbox.archive-retention`
  (30 días por defecto).
- **`outbox.infrastructure.OutboxPublisherJob`** /
  **`OutboxArchiverJob`** (`@Component`, `@ConditionalOnProperty(prefix =
  "outbox", name = "scheduler-enabled", havingValue = "true", matchIfMissing
  = true)`): disparan `PublishPendingOutboxMessages`/
  `ArchivePublishedOutboxMessages` vía `@Scheduled(fixedDelayString =
  "${outbox.poll-interval:PT5S}")` y `@Scheduled(cron =
  "${outbox.archive-cron:...}")` respectivamente. Sin lógica propia.
- **`outbox.infrastructure.OutboxSchedulingConfig`** (`@Configuration
  @EnableScheduling @EnableConfigurationProperties(OutboxProperties.class)`).
- **`application.yml`**: sección `outbox.*` con los valores por defecto de
  la ficha (poll-interval `PT5S`, batch-size `50`, max-attempts `8`,
  claim-timeout `PT5M`, archive-retention `P30D`, archive-cron diario a las
  03:00, scheduler-enabled `true`); `management.endpoints.web.exposure.include`
  ampliado a `health,metrics`.
- **`application-test.yml`**: `outbox.scheduler-enabled: false` — decisión
  explícita para que las decenas de `@SpringBootTest` de otras
  funcionalidades no compitan con un publicador en segundo plano sobre
  filas de `outbox_message` que no les conciernen (ver "Riesgos").

### Pruebas

- **Unitarias (sin base de datos, Mockito):**
  - `OutboxBackoffPolicyTest`: crecimiento exponencial verificable entre
    niveles, jitter siempre no-negativo y acotado, cota inferior nunca por
    debajo de la base.
  - `PublishPendingOutboxMessagesTest`: éxito → `markPublished`; fallo con
    intentos restantes → `markRetry` con `nextAttemptAt` calculado; fallo
    con intentos agotados → `markFailed(id, attemptsFinal, error)`; lote
    vacío no toca el publicador.
  - `RetryFailedOutboxMessageTest`: no encontrado → `ResourceNotFoundException`;
    estado distinto de `FAILED` → `OutboxMessageNotFailedException`;
    `FAILED` → `markRetry(id, 0, null, null)`.
  - `ArchivePublishedOutboxMessagesTest`: calcula el corte con
    `clock.now() - archiveRetention` y delega correctamente.
- **Integración (Testcontainers, `*IntegrationTest.java`), sin sleeps —
  tiempo controlado con `MutableClock` (doble de `shared.domain.Clock`,
  bean `@Primary` inyectado solo en estos tests) en vez de esperar backoffs
  reales:**
  - `PublishPendingOutboxMessagesIntegrationTest`:
    - publicación feliz → `PUBLISHED` con `publishedAt` poblado;
    - `repeatedFailuresGrowTheBackoffThenFail` (con `outbox.max-attempts=3`
      vía `@DynamicPropertySource`): 1er fallo → `PENDING`,
      `attempts=1`, `nextAttemptAt` en el futuro; **no reclamable antes**
      de esa fecha; se avanza el reloj (sin sleep) hasta pasado ese
      instante → 2º fallo, backoff estrictamente mayor que el 1º; se avanza
      de nuevo → 3er fallo (agota `max-attempts=3`) → `FAILED`,
      `attempts=3`, `lastError` con el mensaje de la excepción;
    - `manualRetryAfterExhaustionRepublishesTheMessage`: agota intentos →
      `FAILED`; `RetryFailedOutboxMessage.retry(id)` → `PENDING`,
      `attempts=0`; se reconfigura el mock del publicador para que tenga
      éxito; `publishBatch()` → `PUBLISHED`;
    - `orphanedProcessingMessageIsReclaimedAndPublished`: inserta
      directamente un mensaje `PROCESSING` con `nextAttemptAt` en el
      pasado (simulando un worker muerto) y verifica que
      `publishBatch()` lo reclama y publica.
  - `PublishPendingOutboxMessagesConcurrencyIntegrationTest`: 20 mensajes
    `PENDING`, dos hilos reales llamando a `publishBatch()` en paralelo
    (arrancados a la vez con un `CountDownLatch`, sin sleeps); se verifica
    que la suma de mensajes procesados por ambos hilos es exactamente 20 y
    que cada `eventId` llega al puerto de publicación **exactamente una
    vez** (mapa de conteo por `eventId` bajo el mock) — garantía de
    no-duplicación de dos "instancias" del job gracias al `SKIP LOCKED` de
    T701, ejercitada ahora a través del caso de uso completo (no solo del
    repositorio, como ya hacía `OutboxMessageClaimConcurrencyIntegrationTest`
    de T701).
  - `ArchivePublishedOutboxMessagesIntegrationTest`: inserta un `PUBLISHED`
    antiguo (40 días), uno reciente (1 día), un `PENDING`, un `PROCESSING`
    y un `FAILED`; `archive()` borra solo el primero y dejo el resto
    intactos.
- Se actualizaron los dos tests de T701
  (`OutboxMessageRepositoryAdapterIntegrationTest`) que llamaban a
  `markFailed(id, lastError)` con la firma antigua.

```text
cd backend && mvn -B -Dtest="com.tfp.timetracking.outbox.**" test
```

Resultado: **BUILD SUCCESS**, 28 tests en el módulo `outbox` (unitarios +
integración), todos en verde.

```text
cd backend && mvn -B verify
```

Resultado final: **BUILD SUCCESS**. `Tests run: 266, Failures: 0, Errors: 0,
Skipped: 0` (incluye `LayeredArchitectureTest`, `ModuleCyclesTest`,
`OutboxEncapsulationTest`, `DomainEventImmutabilityTest`, `DomainPurityTest`,
`RestLayerAccessTest`, `ConcurrencyIntegrationTest` de T604, y toda la suite
previa de T701/T702, en verde).

### Cobertura

- `check-domain-coverage` y `check-application-coverage` (JaCoCo, umbrales
  de T101): **"All coverage checks have been met."** Las clases nuevas de
  `outbox.application` (`PublishPendingOutboxMessages`,
  `RetryFailedOutboxMessage`, `ArchivePublishedOutboxMessages`,
  `OutboxBackoffPolicy`, `OutboxMetrics`, `OutboxProperties`) están
  cubiertas directamente por los tests unitarios dedicados y, de forma
  cruzada, por los tests de integración; `outbox.domain.
  OutboxMessageNotFailedException` queda cubierta por
  `RetryFailedOutboxMessageTest`.

### Seguridad

- No se expone ningún endpoint REST nuevo en esta ficha (la ficha no lo
  pedía): `RetryFailedOutboxMessage`/`ArchivePublishedOutboxMessages` son
  casos de uso invocables desde tests/herramientas operativas internas, no
  desde la API pública. Si en el futuro se exponen por HTTP (p. ej. un panel
  de operación), deberán protegerse con un rol administrativo — pendiente,
  fuera de alcance de T703.
- `management.endpoints.web.exposure.include` se amplió a `health,metrics`.
  El endpoint `/actuator/metrics` no expone datos de negocio (solo nombres
  y valores agregados de métricas técnicas: contadores/timers/gauges), pero
  sí es informativo sobre el volumen de actividad del sistema; no se aplicó
  autenticación adicional específica sobre Actuator en esta ficha (no
  estaba definida previamente para `health` tampoco) — queda anotado como
  pendiente de decisión explícita para producción en "Riesgos".
- El puerto `IntegrationEventPublisher` nunca recibe entidades JPA ni datos
  sensibles fuera del envelope ya validado de `IntegrationEvent` (mismo
  contrato que T702). El log estructurado
  (`LoggingIntegrationEventPublisher`) escribe el envelope completo
  (`eventId`, `eventType`, `tenantId`, `aggregateId`, etc.) pero nunca el
  `payload` completo en el mensaje de log de nivel INFO — se decidió no
  volcar el payload a los logs de aplicación por defecto, dado que podría
  contener datos personales (nombres, fechas de fichaje) sin necesidad
  operativa de tenerlos en el log; si se necesita para depuración, debe
  hacerse a nivel `DEBUG` explícito en una iteración posterior.

### Documentación actualizada

- **`docs/integration/outbox-publisher.md`** (nuevo): ciclo de vida completo
  de un mensaje, fórmula de backoff, casos de uso, jobs programados,
  tabla de configuración `outbox.*` y métricas expuestas.
- **`docs/integration/event-catalog.md`**: la nota que decía "usada por el
  futuro publicador (T703)" se actualizó a presente; se añadió una frase
  explícita sobre la garantía at-least-once **real** (ya no solo
  documentada) y una referencia al nuevo documento.

### ADR

- No fue necesaria ADR nueva. Se siguió ADR-0005 al pie de la letra: sin
  broker, publicador por polling, at-least-once, consumidores idempotentes.
  La única implementación del puerto de publicación es un log estructurado,
  como fija explícitamente la ficha.

### Riesgos detectados

1. **Bug de T701 corregido en esta ficha:** `markFailed` no persistía el
   número final de intentos (ver "Cambios"). Se detectó porque el propio
   test de integración de backoff lo hacía evidente
   (`attempts` esperado `3`, observado `2`); se corrigió ampliando la firma
   del puerto (cambio compatible hacia adelante, sin impacto funcional en
   otros consumidores porque `outbox.infrastructure` es el único paquete
   autorizado a usar directamente `OutboxMessageRepository`, verificado por
   `OutboxEncapsulationTest`).
2. **Jitter no determinista en tests:** `OutboxBackoffPolicyTest` usa
   `ThreadLocalRandom` real (no inyectado) y verifica **rangos**, no valores
   exactos, con márgenes calculados para tolerar el jitter máximo del nivel
   anterior. Es una elección deliberada (evitar sobre-ingeniería de una
   fuente de aleatoriedad inyectable para un caso de uso tan acotado) pero
   deja una pequeñísima superficie de no-determinismo teórico en el test;
   los márgenes usados (30s de separación mínima entre niveles cuyo jitter
   máximo es de pocos segundos a niveles bajos) hacen el riesgo de
   flakiness prácticamentente nulo.
3. **`/actuator/metrics` sin autenticación dedicada:** ver "Seguridad".
   Antes de exponer el backend fuera de un entorno de confianza debería
   decidirse si Actuator necesita su propio `SecurityFilterChain` o un
   perfil/red separada — no es una regresión de esta ficha (`health` ya
   estaba expuesto igual de abierto desde T101), pero el nuevo `metrics`
   amplía ligeramente la superficie informativa expuesta.
4. **Sin broker real:** confirmado y esperado por ADR-0005. Si una futura
   iteración necesita entrega push/tiempo real en vez de polling de 5s,
   requerirá una ADR nueva evaluando Kafka/RabbitMQ/similar; no es una
   limitación de esta implementación sino una decisión de arquitectura ya
   tomada conscientemente.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T703.
- Exponer `RetryFailedOutboxMessage`/`ArchivePublishedOutboxMessages` por un
  endpoint HTTP de operación (protegido por rol admin) queda fuera de
  alcance; la ficha solo pedía que fueran "invocables además del
  scheduler", cumplido vía casos de uso Spring inyectables.
- La siguiente tarea correcta es `T704` (idempotencia de consumidores +
  catálogo completo de eventos), que puede apoyarse en el `eventId` estable
  que ya viaja en cada `IntegrationEvent`/mensaje de outbox publicado por
  esta ficha.
