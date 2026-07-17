# ADR-0005: Transactional Outbox con polling, sin broker

* Estado: accepted
* Fecha: 2026-07-17

## Contexto y problema

Se necesita publicar eventos de integración de forma fiable (at-least-once)
sin introducir la complejidad operativa de un broker de mensajería, dado que
el MVP es un monolito modular (ADR-0001).

## Decisión

Implementar el patrón **Transactional Outbox**: los eventos de integración
relevantes se escriben en la tabla `outbox_message` **en la misma
transacción** que el cambio de negocio que los origina. Un publicador
interno (polling) los envía a los consumidores. No se introduce ningún
broker (Kafka/RabbitMQ) en el MVP; hacerlo requeriría un nuevo ADR. Los
eventos de integración son versionados (`eventType` + `eventVersion`),
documentados en `docs/integration/event-catalog.md`, y nunca contienen
entidades JPA ni modelos internos. La entrega es at-least-once; los
consumidores deben ser idempotentes.

## Consecuencias

* (+) Atomicidad entre el cambio de negocio y la publicación del evento sin
  necesidad de un coordinador de transacciones distribuidas.
* (+) No añade infraestructura ni dependencias operativas nuevas al MVP.
* (-) El polling introduce latencia de publicación (no es push instantáneo);
  aceptable para el MVP.
* (-) Requiere pruebas explícitas de idempotencia en los consumidores y de
  atomicidad/reintentos en el publicador.
