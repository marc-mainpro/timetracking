# T704 — Idempotencia, pruebas transaccionales finales y catálogo de eventos

### Cambios

- **Migración `V9__processed_event.sql`** (el número real disponible tras
  `V8__outbox.sql`, de T701; la ficha mencionaba `V7`, ya ocupado por
  `V7__correction_request_version.sql`): tabla
  `processed_event(event_id UUID PRIMARY KEY, processed_at TIMESTAMPTZ NOT NULL)`,
  exclusiva del consumidor de demostración (no es una tabla de negocio).
- **`outbox.application.IntegrationEventListener`** (puerto nuevo,
  `onEvent(IntegrationEvent)`): punto de extensión in-process, notificado
  por `LoggingIntegrationEventPublisher` justo después de loguear cada
  evento, a todos los beans registrados (lista inyectada por Spring, vacía
  si no hay ninguno). Un fallo de un listener se registra en el log pero
  **nunca** se propaga: la publicación (log + `markPublished` en
  `PublishPendingOutboxMessages`) no depende de que un listener tenga
  éxito. Documentado explícitamente (javadoc + ADR-0009) que este hook
  **no** es el mecanismo de entrega a consumidores externos reales
  (ADR-0005 sigue exigiendo log/broker fuera de proceso para eso) y que no
  debe usarse para implementar casos de uso de negocio reales.
- **`outbox.infrastructure.LoggingIntegrationEventPublisher`** — ampliada:
  constructor recibe `List<IntegrationEventListener>`; tras loguear, itera
  la lista y notifica a cada listener dentro de un `try/catch` por
  listener.
- **`outbox.infrastructure.demo`** (paquete nuevo, consumidor de
  demostración, **no** un caso de uso de negocio):
  - `ProcessedEventJpaEntity` / `ProcessedEventJpaRepository`: persistencia
    Spring Data de `processed_event`.
  - `DemoIdempotentEventConsumer` (`@Component`, implementa
    `IntegrationEventListener`): aplica el patrón de idempotencia exigido
    por ADR-0005 a los futuros consumidores reales — comprobar
    `processedEventRepository.existsById(eventId)` antes de aplicar el
    efecto; si no existe, aplicar el efecto (aquí, un contador
    `effectsAppliedCount()` observable en tests) e insertar la marca en la
    misma transacción `@Transactional`; si la inserción falla por
    violación de la clave primaria (condición de carrera entre dos
    "consumidores" procesando el mismo evento a la vez,
    `DataIntegrityViolationException`), se trata igual que un duplicado.
    Expone `consume(IntegrationEvent): ConsumptionResult` (`PROCESSED` /
    `DUPLICATE_IGNORED`) además de `onEvent(...)` (delega en `consume`),
    para que los tests puedan invocar una redelivery deliberada sin pasar
    por el publicador.
- **`docs/integration/event-catalog.md`** — completado íntegramente (antes
  placeholder de T702/T703): para cada uno de los 8 tipos de evento
  (`tenant.registered.v1`, `identity.employee-created.v1`,
  `identity.employee-deactivated.v1`, `time-tracking.workday-started.v1`,
  `time-tracking.workday-closed.v1`, `corrections.correction-requested.v1`,
  `corrections.correction-approved.v1`, `corrections.correction-rejected.v1`):
  módulo productor, disparador de negocio, `aggregateId`, esquema del
  `payload` con ejemplo JSON completo del envelope, y nota de idempotencia
  específica. Se añadieron también: tabla de campos del envelope, sección
  de semántica de entrega at-least-once (con la implicación de orden no
  garantizado entre agregados distintos), sección "Idempotencia de
  consumidores" con el patrón de referencia paso a paso y el ejemplo
  concreto de este backend, y la política de versionado
  (`.v2` para cambios incompatibles, nunca mutar `.v1`).
- **`docs/adr/ADR-0009-consumidor-demo-idempotencia-eventos.md`** (nueva):
  formaliza la decisión de añadir `IntegrationEventListener` como hook
  in-process exclusivamente para la demostración de idempotencia, y deja
  explícito por qué **no** es (ni debe convertirse en) el canal de entrega
  a consumidores reales. Ver "ADR" más abajo.

### Pruebas

- **`OutboxGuaranteesIntegrationTest`** (Testcontainers PostgreSQL, nombrada
  así — no `OutboxGuaranteesIT` como sugería la ficha — para cumplir la
  convención del proyecto de que los tests de integración terminan en
  `IntegrationTest.java`, sin Failsafe configurado): flujo de extremo a
  extremo completo de la iteración 7.
  1. Abre y cierra una jornada real vía `MockMvc` (`TestTenantFactory`,
     mismo patrón que `EndWorkdayUseCaseAtomicityIntegrationTest` de T702).
  2. Re-verifica la atomicidad negocio+outbox a nivel de flujo completo: el
     mensaje `time-tracking.workday-closed.v1` queda `PENDING` en
     `outbox_message` inmediatamente tras el cierre, y `processed_event`
     no tiene fila para ese `eventId` todavía.
  3. Invoca `PublishPendingOutboxMessages.publishBatch()` (caso de uso
     real, sin mocks del publicador): el mensaje pasa a `PUBLISHED`, y el
     consumidor de demostración —enganchado como listener— ya lo procesó
     una vez (fila en `processed_event`, contador de efectos
     incrementado).
  4. Reconstruye el mismo `IntegrationEvent` (mismo `eventId`) a partir de
     los datos ya persistidos en `outbox_message` y llama a
     `DemoIdempotentEventConsumer.consume(...)` **dos veces más**,
     simulando la redelivery deliberada que un canal at-least-once
     produciría: ambas llamadas devuelven `DUPLICATE_IGNORED`, el contador
     de efectos no crece, y sigue habiendo exactamente una fila en
     `processed_event` para ese `eventId`.
- **`LoggingIntegrationEventPublisherTest`** (unitaria, sin base de datos):
  notifica a todos los listeners registrados; un listener que lanza
  excepción no impide notificar a los demás ni afecta el resultado de
  `publish(...)`; funciona sin listeners registrados; no notifica nada
  antes de que se invoque `publish(...)`.
- **`DemoIdempotentEventConsumerTest`** (unitaria, Mockito): procesa y
  registra un evento nuevo; ignora un evento ya marcado como procesado;
  trata una condición de carrera (`DataIntegrityViolationException` al
  insertar) como duplicado en vez de propagar el error; `onEvent(...)`
  delega correctamente en `consume(...)`.
- **`FlywayProcessedEventMigrationIntegrationTest`**: la migración crea la
  tabla `processed_event` desde una base de datos vacía, con las columnas
  esperadas y `event_id` como clave primaria (mismo patrón que
  `FlywayOutboxMigrationIntegrationTest` de T701).

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 278, Failures: 0, Errors: 0,
Skipped: 0` (incluye `LayeredArchitectureTest`, `ModuleCyclesTest`,
`OutboxEncapsulationTest`, `DomainEventImmutabilityTest`, `DomainPurityTest`,
`RestLayerAccessTest`, `ConcurrencyIntegrationTest` de T604, y toda la suite
previa de T701/T702/T703, en verde). El criterio de aceptación global de la
ficha ("los reintentos no producen efectos duplicados") queda cubierto
explícitamente por `OutboxGuaranteesIntegrationTest`.

### Cobertura

- `check-domain-coverage` y `check-application-coverage` (JaCoCo, umbrales
  de T101): **"All coverage checks have been met."**
  `outbox.application.IntegrationEventListener` es una interfaz sin cuerpo
  ejecutable (igual que `IntegrationEventPublisher` ya existente), no
  aporta líneas que cubrir. `DemoIdempotentEventConsumer` y
  `ProcessedEventJpaEntity`/`ProcessedEventJpaRepository` viven en
  `outbox.infrastructure.demo`, fuera del alcance de los umbrales
  domain/application, pero quedan cubiertos igualmente por
  `DemoIdempotentEventConsumerTest` (unitario) y
  `OutboxGuaranteesIntegrationTest` (integración).

### Seguridad

- No se expone ningún endpoint REST nuevo. `processed_event` y el
  consumidor de demostración no son alcanzables desde la API pública; solo
  se ejercitan desde código interno del proceso y desde tests.
- El `payload` reconstruido en `OutboxGuaranteesIntegrationTest` para la
  redelivery deliberada se lee de `outbox_message` (ya escrito por el
  propio backend en la misma transacción del negocio), nunca de una
  entrada externa no confiable.
- `IntegrationEventListener.onEvent(...)` se invoca dentro del mismo
  proceso que publica; no introduce ninguna superficie de red nueva. Ver
  ADR-0009 sobre por qué este hook no debe convertirse en el canal de
  consumidores externos reales (evitaría, entre otras cosas, que datos de
  `payload` crucen un límite de proceso sin la validación/serialización
  explícita que tendría un canal real).

### Documentación actualizada

- **`docs/integration/event-catalog.md`**: completado íntegramente (ver
  "Cambios"). Sustituye el placeholder de T702/T703.
- **`docs/adr/ADR-0009-consumidor-demo-idempotencia-eventos.md`** (nueva).
- **`tasks/STATUS.md`**: fila `T704` actualizada de "pendiente" a "hecha".

### ADR

- **ADR-0009 (nueva):** formaliza la decisión, no fijada previamente en
  ADR-0005 ni en el SDD, de cómo se engancha un consumidor de
  demostración cuando no existe ningún canal real de entrega (ADR-0005
  prohíbe un broker en el MVP). Deja explícito que
  `IntegrationEventListener` es un punto de extensión in-process
  exclusivamente para demostrar/probar el patrón de idempotencia — no el
  mecanismo de entrega a consumidores reales — para prevenir que un futuro
  desarrollador lo use accidentalmente para acoplar un caso de uso de
  negocio real al publicador del outbox.
- No se amplió ADR-0005: sus decisiones centrales (sin broker, polling,
  at-least-once, consumidores idempotentes) siguen intactas; ADR-0009 es
  una decisión de implementación derivada, no una revisión de esa
  decisión.

### Riesgos detectados

1. **Hook in-process mal utilizado en el futuro:** el mayor riesgo de esta
   ficha es que `IntegrationEventListener` se use más adelante para
   implementar un consumidor de negocio real, reacoplando productor y
   consumidor síncronamente dentro del mismo proceso (justo lo que el
   Transactional Outbox existe para evitar). Mitigado con documentación
   explícita en el javadoc de la interfaz y en ADR-0009, pero sigue siendo
   una responsabilidad de revisión de código, no algo que ArchUnit pueda
   impedir automáticamente sin una regla dedicada (no se añadió una regla
   ArchUnit específica para esto: se consideró excesivo para un único
   punto de extensión ya fuertemente documentado; si en el futuro
   aparecieran más listeners "reales", valdría la pena reconsiderarlo).
2. **Listener lento degradaría la latencia del lote de publicación:** el
   hook corre en el mismo hilo que `PublishPendingOutboxMessages`. Para el
   consumidor de demostración (una fila insertada por evento) el impacto
   es despreciable; documentado en ADR-0009 como una limitación conocida
   si en algún momento se añadiera un listener con trabajo pesado
   (no debería: un consumidor real con trabajo pesado debe ser un proceso
   externo).
3. **`processed_event` es de un único "consumidor" lógico:** la tabla no
   tiene una columna `consumer_name`, así que si en el futuro se
   necesitaran varios consumidores de demostración simultáneos
   deduplicando de forma independiente, esta tabla tal cual no serviría
   para más de uno (colisionarían en la misma clave `event_id`). No es un
   problema hoy (solo hay un consumidor de demostración) y está
   documentado en el catálogo de eventos ("no debe reutilizarse"); un
   consumidor real futuro necesitará su propia tabla.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T704 ni la iteración 7. El flujo completo
  negocio → outbox → publicador → consumidor idempotente queda demostrado
  de extremo a extremo, con `mvn -B verify` en verde.
- T704 es la última ficha de la iteración 7. No queda ninguna ficha
  pendiente en esa iteración según `tasks/STATUS.md`.
