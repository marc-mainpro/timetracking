# ADR-0009: Punto de extensión in-process para el consumidor de demostración idempotente

* Estado: accepted
* Fecha: 2026-07-20

## Contexto y problema

ADR-0005 exige que los consumidores de eventos de integración sean
idempotentes (deduplicar por `eventId`), porque la entrega es
at-least-once. Esa exigencia estaba documentada pero no **demostrada**: no
existía ninguna prueba de extremo a extremo que ejercitara el patrón de
deduplicación contra un consumidor real, ni ningún ejemplo de código que un
futuro equipo pudiera copiar al construir un consumidor real.

Al mismo tiempo, ADR-0005 prohíbe introducir un broker de mensajería en el
MVP; la única implementación de `IntegrationEventPublisher` es un log
estructurado (`LoggingIntegrationEventPublisher`). No hay, por tanto, ningún
canal real por el que un "consumidor" pueda suscribirse a los eventos
publicados: hace falta decidir cómo se engancha un consumidor de
demostración sin simular un broker que no existe ni adelantar una decisión
de infraestructura que le corresponde a una ADR futura.

## Decisión

* Se añade el puerto `outbox.application.IntegrationEventListener`
  (`onEvent(IntegrationEvent)`), notificado por `LoggingIntegrationEventPublisher`
  **después** de loguear cada evento, a todos los beans que lo implementen
  (lista inyectada por Spring, vacía si no hay ninguno).
* Un fallo de un listener se registra en el log pero nunca se propaga: la
  publicación (log + `markPublished` en `PublishPendingOutboxMessages`)
  nunca depende de que un listener tenga éxito.
* La única implementación en el MVP es
  `outbox.infrastructure.demo.DemoIdempotentEventConsumer`, que aplica el
  patrón de idempotencia (comprobar `processed_event` antes de aplicar el
  efecto, insertar la marca en la misma transacción, tratar una violación
  de clave primaria como duplicado) que ADR-0005 exige a los consumidores
  reales.
* **Este hook no es, y no debe convertirse en, el mecanismo de entrega a
  consumidores externos reales.** Mientras ADR-0005 siga vigente, la única
  forma "real" de consumir eventos de integración sigue siendo leer el log
  estructurado (o, en el futuro, un broker introducido por una ADR nueva
  que sustituya `LoggingIntegrationEventPublisher`). `IntegrationEventListener`
  no debe usarse para implementar casos de uso de negocio reales en
  proceso: hacerlo reacoplaría productor y consumidor síncronamente dentro
  del mismo despliegue, exactamente lo que el patrón Transactional Outbox
  existe para evitar.
* La tabla `processed_event` (migración `V9__processed_event.sql`) es
  exclusiva de este consumidor de demostración; un consumidor real futuro
  necesitará su propia tabla de deduplicación (u otro mecanismo
  equivalente), no debe reutilizar esta.

## Consecuencias

* (+) El criterio de aceptación "los reintentos no producen efectos
  duplicados" (ADR-0005, T704) queda demostrado por una prueba de
  extremo a extremo real (`OutboxGuaranteesIntegrationTest`): acción de
  negocio → outbox `PENDING` → publicador `PUBLISHED` → consumidor
  idempotente, incluida una redelivery deliberada del mismo `eventId`.
* (+) Deja un ejemplo de código concreto y documentado
  (`docs/integration/event-catalog.md`, sección "Idempotencia de
  consumidores") que un futuro consumidor real puede replicar.
* (-) Introduce un punto de extensión in-process que, mal usado, podría
  tentar a acoplar un caso de uso de negocio real directamente al
  publicador del outbox. Se mitiga documentando explícitamente la
  prohibición (aquí y en el javadoc de `IntegrationEventListener`); si en
  el futuro aparece un consumidor real no trivial, debe construirse como
  servicio externo que lee el canal de publicación vigente en ese momento
  (log o broker), no como un `IntegrationEventListener` adicional.
* (-) El hook corre en el mismo hilo y la misma invocación que
  `PublishPendingOutboxMessages`; un listener lento degradaría la latencia
  de publicación del lote (aceptable para un consumidor de demostración
  con una sola fila insertada por evento; no sería aceptable para un
  consumidor real con trabajo pesado, que debe ser un proceso externo).
