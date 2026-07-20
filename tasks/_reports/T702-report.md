## T702 — Eventos de integración + atomicidad en Outbox

### Cambios

- **`IntegrationEvent`** (`shared.domain.IntegrationEvent`, record): envelope
  `eventId, eventType, eventVersion, occurredAt, tenantId, aggregateId,
  aggregateType, payload`. Se añadió `aggregateType` (no listado en el
  envelope público de `docs/integration/event-catalog.md`) porque
  `OutboxWriter`/`OutboxMessage` lo necesitan para poblar la columna
  `aggregate_type` de `outbox_message`; es una extensión deliberada, no un
  campo del contrato externo.
  - **Ubicación (desviación consciente respecto al enunciado):** la ficha
    sugería mappers en `*/application/integration/` sin más detalle sobre
    dónde vive `IntegrationEvent`. Se probó primero colocarlo en
    `outbox.domain` (como parecía natural, junto a `OutboxMessage`), pero eso
    genera un ciclo de dependencia real: los mappers de cada módulo de
    negocio (`identity`, `tenant`, `timetracking`, `corrections`) construyen
    `IntegrationEvent`, y `outbox.infrastructure.OutboxDomainEventPublisher`
    a su vez depende de esos mappers para saber traducir cada evento de
    dominio → ciclo `outbox -> módulo -> outbox`, detectado por
    `ModuleCyclesTest` (ArchUnit, `slices().should().beFreeOfCycles()`). Se
    movió `IntegrationEvent` a `shared.domain` (junto a
    `DomainEventPublisher`, el puerto por el que ya transitan estos eventos):
    todos los módulos pueden depender de `shared` sin ciclo.
- **Mappers `DomainEvent -> IntegrationEvent`**, uno por módulo, en
  `*/application/integration/` (clases finales con método estático
  `map(Object): Optional<IntegrationEvent>`, sin Spring, mismo estilo que los
  demás mappers del proyecto):
  - `tenant.application.integration.TenantIntegrationEventMapper`:
    `TenantRegistered` → `tenant.registered.v1`.
  - `identity.application.integration.IdentityIntegrationEventMapper`:
    `EmployeeCreated` → `identity.employee-created.v1`,
    `EmployeeDeactivated` → `identity.employee-deactivated.v1`.
  - `timetracking.application.integration.TimeTrackingIntegrationEventMapper`:
    `WorkdayStarted` → `time-tracking.workday-started.v1`, `WorkdayClosed` →
    `time-tracking.workday-closed.v1`. **`BreakStarted`/`BreakEnded` NO se
    traducen** (devuelven `Optional.empty()`), documentado en el Javadoc de
    la clase y en `docs/integration/event-catalog.md`: son eventos de interés
    solo interno (consistencia del agregado `Workday`), ningún consumidor
    externo previsto los necesita.
  - `corrections.application.integration.CorrectionsIntegrationEventMapper`:
    `CorrectionRequested/Approved/Rejected` → los tres tipos
    `corrections.correction-*.v1`.
- **`OutboxDomainEventPublisher`** (`outbox.infrastructure`, `@Component`):
  sustituye a `LoggingDomainEventPublisher` (T203, **eliminada**, ver
  decisión más abajo) como implementación de `DomainEventPublisher`. Por
  cada evento de dominio recibido, prueba los 4 mappers en orden
  (`.or(...)` encadenado) y, si alguno traduce, llama a
  `OutboxWriter#write(IntegrationEvent)`. Vive en `outbox.infrastructure`
  (no en `shared.infrastructure`) porque es la única pieza que necesita
  conocer los mappers de los 4 módulos de negocio; `shared` sigue sin
  depender de ningún módulo de negocio concreto.
- **`OutboxWriter` (`outbox.application`) — firma ajustada:** T701 la dejó
  como `write(aggregateType, aggregateId, eventType, eventVersion, payload)`.
  Se cambió a `write(IntegrationEvent event)` porque, con la firma anterior,
  el `eventId` y el `occurredAt` que ya calcula el mapper (reutilizando el
  `eventId` del evento de dominio de origen, para trazabilidad/idempotencia
  en consumidores externos, y el instante real del hecho de negocio) se
  habrían descartado y `JpaOutboxWriter` los habría vuelto a generar,
  perdiendo esa información. Documentado en el Javadoc del puerto.
- **`JpaOutboxWriter`** actualizado a la nueva firma; ya no depende de
  `IdGenerator` (usa `event.eventId()`) ni de `TenantContext` para el
  `tenantId` de la fila (usa `event.tenantId()`, ver "Riesgos" para el porqué
  — descubierto durante las pruebas, no en el diseño inicial).
- **`ObjectMapper` dedicado** (`shared.infrastructure.JacksonConfig`):
  `Jackson2ObjectMapperBuilder.json()` + `JavaTimeModule` explícito +
  `WRITE_DATES_AS_TIMESTAMPS` deshabilitado. Es el único bean `ObjectMapper`
  del contexto (Spring Boot no crea el suyo si ya existe uno definido por la
  app), así que también sirve a Spring MVC para los DTOs REST — sin cambio
  de comportamiento respecto al autoconfigurado por defecto (mismos módulos:
  JSR-310, JDK8, nombres de parámetros vía `findAndRegisterModules()`).
- `LoggingDomainEventPublisher` **eliminada** (no se mantiene como fallback
  por perfil): dejar dos implementaciones candidatas del mismo puerto (una
  que persiste, otra que solo loguea) es una fuente de bugs de despliegue si
  el perfil equivocado queda activo, y no hay ningún caso de uso real en este
  proyecto para "eventos de dominio que no se persisten".
- `shared.domain.DomainEventPublisher`: Javadoc actualizado (ya no dice
  "T702 la sustituirá", ahora describe la implementación real).

### Pruebas

- **Unitarias de mapeo** (`*IntegrationEventMapperTest`, una por módulo):
  envelope completo (`eventId`, `eventType`, `eventVersion`, `occurredAt`,
  `tenantId`, `aggregateId`, `aggregateType`), payload mínimo correcto por
  tipo, y devuelven `Optional.empty()` para eventos no reconocidos.
  `TimeTrackingIntegrationEventMapperTest` verifica explícitamente que
  `BreakStarted`/`BreakEnded` no producen evento de integración.
- **`OutboxDomainEventPublisherTest`** (unitaria, `OutboxWriter` mockeado):
  escribe cuando hay traducción, no escribe para eventos sin traducción, no
  escribe con lista vacía.
- **`EndWorkdayUseCaseAtomicityIntegrationTest`** (Testcontainers, caso de
  referencia explícito de la ficha): cerrar una jornada real vía HTTP deja
  exactamente una fila `PENDING` en `outbox_message` con
  `time-tracking.workday-closed.v1` y el payload correcto
  (`workdayId`/`employeeId`/`startedAt`/`endedAt` coinciden con la respuesta
  HTTP), en la misma transacción del cierre.
- **`RegisterTenantUseCaseOutboxCommitIntegrationTest`** (Testcontainers,
  prueba de commit genérica): registrar un tenant (que publica dos eventos de
  dominio de dos módulos distintos en la misma llamada a `publish(...)`,
  `TenantRegistered` + `EmployeeCreated`) deja dos filas `PENDING` en
  `outbox_message`, una por tipo, verificando que el publicador traduce
  correctamente eventos de módulos distintos en una sola invocación.
- **`ApproveCorrectionRequestUseCaseAtomicityIntegrationTest`** (Testcontainers,
  **prueba de atomicidad más importante de la tarea**): fuerza un fallo en
  `AuditRecorder.record(...)`, que en `ApproveCorrectionRequestUseCase` se
  invoca **después** de `domainEventPublisher.publish(...)`. Esto garantiza
  que la fila de outbox ya se había escrito (dentro de la transacción) cuando
  se produce el fallo. Tras el rollback de Spring: cero filas en
  `outbox_message` para esa corrección, `correction_request.status` sigue
  `PENDING`, `workday.status` sigue `CLOSED` (no `ADJUSTED`) y cero filas en
  `audit_event` — confirma que el outbox participa de la misma transacción
  atómica que el resto del caso de uso, no solo que "no se publica nada".
- Se mantiene en verde `OutboxEncapsulationTest` (sin tocar) y se añadió
  cobertura nueva al `ModuleCyclesTest` existente (ya pasaba, pero ahora
  ejercita el nuevo grafo de dependencias `outbox <-> módulos`).

```text
cd backend && mvn -B -Dtest=TenantIntegrationEventMapperTest,IdentityIntegrationEventMapperTest,TimeTrackingIntegrationEventMapperTest,CorrectionsIntegrationEventMapperTest,OutboxDomainEventPublisherTest,RegisterTenantUseCaseAtomicityIntegrationTest,RegisterTenantUseCaseOutboxCommitIntegrationTest,EndWorkdayUseCaseAtomicityIntegrationTest,ApproveCorrectionRequestUseCaseAtomicityIntegrationTest test
```

Resultado: **BUILD SUCCESS**, todos los tests anteriores en verde.

```text
cd backend && mvn -B verify
```

Resultado final: **BUILD SUCCESS**. `Tests run: 209, Failures: 0, Errors: 0, Skipped: 0`
(incluye `LayeredArchitectureTest`, `ModuleCyclesTest`, `OutboxEncapsulationTest`,
`DomainEventImmutabilityTest`, `DomainPurityTest`, `RestLayerAccessTest` en verde).

### Cobertura

- `check-domain-coverage` y `check-application-coverage` (JaCoCo, umbrales
  ≥90%/≥80% de T101): **"All coverage checks have been met."** Los 4 mappers
  quedan en `*.application.integration`, cubiertos directamente por sus
  tests unitarios dedicados (rutas felices y de exclusión); el nuevo
  `IntegrationEvent` (`shared.domain`) queda cubierto indirectamente por los
  mismos tests y por los 3 IT de atomicidad.

### Seguridad

- El `tenantId` persistido en `outbox_message` se toma de
  `IntegrationEvent#tenantId()` (viene del evento de dominio, calculado por
  el propio agregado), **no** de `TenantContext`. Se descubrió durante las
  pruebas que exigir un `TenantContext` autenticado en `JpaOutboxWriter`
  (diseño inicial) rompe `RegisterTenantUseCase` en producción: el registro
  de un tenant nuevo ocurre en el endpoint público `/api/v1/auth/register`,
  **sin JWT todavía** (el tenant/usuario aún no existen), así que
  `JwtTenantContext.currentTenantId()` lanza `IllegalStateException` ("No hay
  autenticación JWT activa"). Como el evento de dominio ya lleva un
  `tenantId` calculado internamente (nunca a partir de un valor de request
  sin validar), usarlo es seguro y además coincide exactamente con
  `TenantContext.currentTenantId()` en todos los flujos autenticados
  (`Workday.start(tenantId, ...)` ya recibe ese mismo valor). Documentado en
  el Javadoc de `JpaOutboxWriter` y `OutboxWriter`.
- Los módulos de negocio siguen sin depender de infraestructura de outbox:
  `OutboxEncapsulationTest` sigue en verde sin cambios. Los mappers solo
  importan `shared.domain.IntegrationEvent` y sus propios tipos de dominio.
- `ModuleCyclesTest` (ArchUnit, ya existente) fue el que detectó el ciclo
  `outbox -> módulo -> outbox` descrito arriba; sirvió exactamente para lo
  que estaba pensado.

### Documentación actualizada

- `docs/integration/event-catalog.md`: se mantiene como placeholder de T704,
  pero se añadió una nota de implementación (`aggregateType` interno, no
  parte del envelope público), la lista explícita de eventos de dominio
  excluidos (`BreakStarted`/`BreakEnded`) y una regla que describe el flujo
  mapper → `OutboxDomainEventPublisher` → `OutboxWriter`.

### ADR

- No fue necesaria ADR nueva; se siguió ADR-0005 (Transactional Outbox) sin
  contradecirla. La decisión de mover `IntegrationEvent` a `shared.domain` y
  de usar `event.tenantId()` en `JpaOutboxWriter` se documentó en el Javadoc
  de las clases afectadas en vez de en una ADR, por ser detalles de
  implementación del mismo mecanismo ya decidido en ADR-0005.

### Riesgos detectados

1. `OutboxDomainEventPublisher` prueba los 4 mappers en orden fijo
   (`tenant → identity → timetracking → corrections`) hasta que uno
   devuelve `Optional.of(...)`. Como cada evento de dominio es un tipo Java
   concreto y cada mapper hace `instanceof` sobre sus propios tipos, no hay
   riesgo real de colisión entre módulos — pero si en el futuro dos módulos
   llegaran a compartir un mismo tipo de evento de dominio (no ocurre hoy),
   el orden importaría. No bloqueante.
2. `archivePublishedBefore` (T701) purga físicamente filas `PUBLISHED`; no
   cambia en esta tarea. El publicador real que consume `outbox_message`
   (marca `PROCESSING`/`PUBLISHED`, hace I/O de red) es T703 — con T702 las
   filas quedan `PENDING` indefinidamente hasta que T703 exista, lo cual es
   esperado y no bloqueante para esta ficha.
3. `RegisterTenantUseCaseAtomicityIntegrationTest` (de T203) sigue probando
   solo que no queda un tenant huérfano; no se modificó porque su fallo
   ocurre antes de llegar a `publish(...)`, así que no ejercita el outbox.
   La cobertura de atomicidad del outbox específicamente la aportan los 2
   tests nuevos de esta ficha.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T702.
- La siguiente tarea correcta es `T703` (publicador real que reclama
  `outbox_message` vía `OutboxMessageRepository.claimBatch(...)` de T701 y
  lo entrega a un broker/consumidor externo).
