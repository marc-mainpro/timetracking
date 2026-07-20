## T701 — Migración y modelo Outbox

### Cambios

- `V8__outbox.sql` (la ficha mencionaba `V6__outbox.sql`, pero `V6` ya estaba
  ocupado por `V6__audit.sql` y `V7` por
  `V7__correction_request_version.sql`; se verificó `ls
  backend/src/main/resources/db/migration/` y se usó `V8`, el siguiente
  número libre). Crea `outbox_message` exactamente con las columnas de SDD
  §14.2 y el índice compuesto `(status, next_attempt_at)` para el poller.
- `outbox.domain`: `OutboxMessage` (record), `OutboxMessageStatus`
  (`PENDING|PROCESSING|PUBLISHED|FAILED`) y el puerto
  `OutboxMessageRepository` (uso interno del módulo `outbox`, no expuesto al
  resto de la aplicación).
- `outbox.application`: puerto `OutboxWriter#write(aggregateType,
  aggregateId, eventType, eventVersion, payload)` — el único punto de acceso
  que usará el resto de módulos (T702). Genera `id`/`tenantId`/`occurredAt`
  internamente (mismo patrón que `AuditRecorder`) y persiste el mensaje en
  `PENDING`. No publica nada por sí mismo.
- `outbox.infrastructure`:
  - `JpaOutboxWriter` implementa `OutboxWriter`.
  - `persistence/OutboxMessageJpaEntity` + `OutboxMessageMapper` (payload
    `JSONB` vía `@ColumnTransformer`, mismo estilo que `audit`/`corrections`).
  - `persistence/OutboxMessageJpaRepository`: query nativa con CTE
    `WITH candidate AS (SELECT ... FOR UPDATE SKIP LOCKED) UPDATE ...
    RETURNING *` que reclama y marca `PROCESSING` en una única sentencia
    atómica (evita la ventana de carrera entre "seleccionar" y "marcar"); más
    `markPublished`, `markRetry(attempts, nextAttemptAt, lastError)`,
    `markFailed` y `archivePublishedBefore(before)` como updates/`DELETE`
    nativos `@Modifying`.
  - `persistence/OutboxMessageRepositoryAdapter` implementa el puerto de
    dominio y expone la reclamación con un parámetro `leaseExpiresAt`: al
    reclamar, además de marcar `PROCESSING`, fija `next_attempt_at` como
    "lease" de visibilidad — así un `PROCESSING` cuyo worker murió sin marcar
    resultado se puede volver a reclamar cuando ese lease vence (recuperación
    de huérfanos, tal y como pide la ficha).
- Se mantiene intacto `OutboxEncapsulationTest` (ArchUnit ya existente): sigue
  en verde sin cambios, la nueva infraestructura de `outbox.infrastructure`
  solo se usa dentro de `outbox..`.

### Diseño no explícito en la ficha (decisiones tomadas)

- La ficha no detalla el criterio exacto de "PENDING listos para reclamar":
  se interpretó como `next_attempt_at IS NULL OR next_attempt_at <= now`,
  de forma que `markRetry` (que vuelve el mensaje a `PENDING` con un
  `next_attempt_at` futuro) implemente correctamente el backoff — si no se
  filtrara por fecha, el backoff sería inútil porque el poller volvería a
  reclamar el mensaje inmediatamente.
- `archivePublishedBefore` se implementó como `DELETE` de mensajes
  `PUBLISHED` anteriores al instante dado (no hay tabla de archivo separada
  en la migración ni en el SDD); es una purga, coherente con "at-least-once,
  consumidores idempotentes" (ADR-0005) — una vez publicado y purgado no hay
  reintento posible, pero eso ya es responsabilidad del publicador (T703),
  que solo purgará mensajes ya confirmados como publicados.

### Pruebas

- `FlywayOutboxMigrationIntegrationTest`: aplica la migración desde BD vacía,
  verifica existencia de tabla, columnas e índice compuesto.
- `OutboxMessageRepositoryAdapterIntegrationTest`: transiciones de estado
  completas — `save` → `PENDING`; `claimBatch` → `PROCESSING` con lease;
  mensajes `PENDING` con `next_attempt_at` futuro no se reclaman;
  `markPublished` → `PUBLISHED`; `markRetry` → vuelve a `PENDING` con
  `attempts`/`lastError` y no es reclamable hasta que vence el backoff;
  `markFailed` → `FAILED` terminal, no reclamable; `archivePublishedBefore`
  purga solo lo `PUBLISHED` antiguo.
- `OutboxMessageClaimConcurrencyIntegrationTest` (prueba crítica pedida):
  dos "workers" reales reclamando en paralelo. El worker A abre una
  transacción real (`TransactionTemplate`), reclama, y se bloquea en un
  `CountDownLatch` **antes de hacer commit** (sin sleeps, mismo criterio que
  `concurrency.ConcurrencyIntegrationTest` de T604) para garantizar que sus
  filas siguen bloqueadas mientras el worker B reclama concurrentemente.
  Se verifica que ambos conjuntos reclamados son disjuntos y que las 3 filas
  totales quedan `PROCESSING` exactamente una vez.
- `OutboxEncapsulationTest` (ArchUnit ya existente): sigue verde.

```text
cd backend && mvn -B -Dtest=FlywayOutboxMigrationIntegrationTest,OutboxMessageRepositoryAdapterIntegrationTest,OutboxMessageClaimConcurrencyIntegrationTest,OutboxEncapsulationTest test
```

Resultado: **BUILD SUCCESS**. `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`.

```text
cd backend && mvn -B verify
```

Resultado final: **BUILD SUCCESS**. `Tests run: 190, Failures: 0, Errors: 0, Skipped: 0`.

### Cobertura

- `check-domain-coverage` (≥90% línea en `*.domain`) y
  `check-application-coverage` (≥80% línea en `*.application`): **"All
  coverage checks have been met."** — `outbox.domain` queda cubierto de
  forma indirecta por los tests de integración (que ejercitan `OutboxMessage`
  y `OutboxMessageStatus` a través del mapper y el repositorio, igual que
  `AuditEvent` en el módulo `audit`); `outbox.application` solo contiene la
  interfaz `OutboxWriter` (sin bytecode instrumentable propio, igual que
  otros puertos del proyecto).

### Seguridad

- `outbox_message` no tiene FK a `tenant`/entidades de negocio (igual que
  `audit_event`): es una tabla de infraestructura de mensajería, no un
  recurso multitenant expuesto por API en esta tarea. No hay endpoint REST
  nuevo en T701 (el paquete `outbox.interfaces.rest` sigue vacío).
- El acceso a `outbox.infrastructure` sigue restringido al propio módulo por
  `OutboxEncapsulationTest`; el resto de la aplicación solo podrá usar
  `OutboxWriter` (T702).

### Documentación actualizada

- Ninguna adicional: ADR-0005 ya documentaba la decisión y no se contradice;
  `docs/integration/event-catalog.md` sigue como placeholder para T704.

### ADR

- No fue necesaria ADR nueva; se siguió ADR-0005 sin modificarla.

### Riesgos detectados

1. El `lease` de recuperación de huérfanos (`next_attempt_at` fijado al
   reclamar) depende de que el publicador (T703) elija una duración de lease
   razonable respecto a su intervalo de polling; si el lease es demasiado
   corto, un mensaje aún en curso podría reclamarse dos veces por workers
   distintos (violaría at-least-once del lado "solo una vez en curso", no la
   garantía at-least-once de entrega en sí). Es una decisión de configuración
   que corresponde a T703, no a este esqueleto.
2. `archivePublishedBefore` purga físicamente filas; si en el futuro se
   necesita trazabilidad histórica de mensajes publicados, haría falta mover
   a una tabla de archivo en lugar de borrar. No bloqueante para T701 porque
   no hay tal requisito en el SDD ni en la ficha.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T701.
- La siguiente tarea correcta es `T702` (conectar `OutboxWriter` desde las
  transacciones de negocio y definir los eventos de integración concretos).
