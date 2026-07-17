# Skill: review-outbox

## Objetivo

Revisar que el mecanismo de Transactional Outbox (persistencia, publicador,
reintentos) mantiene atomicidad, entrega at-least-once e idempotencia, sin
introducir un broker.

## Entradas

- Código del módulo `outbox` y de los casos de uso que escriben en
  `outbox_message`.
- ADR-0005 (`docs/adr/ADR-0005-transactional-outbox-sin-broker.md`).

## Pasos

1. Verificar que toda escritura en `outbox_message` ocurre dentro de la
   misma transacción que el cambio de negocio (mismo método
   `@Transactional`, mismo commit/rollback).
2. Verificar que el publicador (polling) marca los mensajes como enviados de
   forma que un reintento tras fallo no duplique el envío de forma
   perjudicial para un consumidor idempotente (p. ej. columna `status`/
   `sent_at`/contador de intentos).
3. Confirmar que ningún mensaje se publica con datos de una entidad JPA
   serializada directamente ni con campos internos no documentados.
4. Confirmar que no se ha introducido ninguna dependencia de broker
   (Kafka/RabbitMQ) sin un ADR nuevo que reemplace o complemente el
   ADR-0005.
5. Revisar el manejo de errores del publicador: un fallo de envío no debe
   perder el mensaje (permanece pendiente para reintento) ni bloquear
   indefinidamente la cola completa.

## Validaciones

- Atomicidad: cambio de negocio y mensaje Outbox se confirman o revierten
  juntos.
- At-least-once: un mensaje no confirmado como entregado se reintenta.
- Idempotencia: existe una estrategia de deduplicación en el lado
  consumidor (o está documentada como requisito para consumidores externos)
  usando `eventId`.
- No hay broker introducido fuera de un ADR explícito.

## Pruebas

- Test de integración: si el caso de uso lanza una excepción tras el cambio
  de negocio pero antes de commit, ni el cambio ni el mensaje Outbox
  persisten.
- Test que simula un fallo de publicación y verifica que el mensaje sigue
  pendiente y se reintenta.
- Test que simula un reintento duplicado y verifica que el consumidor (o el
  contrato documentado) trata el `eventId` repetido de forma idempotente.

## Criterios de finalización

- Todos los tests de atomicidad y reintentos en verde.
- Ningún hallazgo abierto de las validaciones anteriores.

## Archivos que puede modificar

- `backend/src/main/java/com/tfp/timetracking/outbox/**`
- `backend/src/test/java/com/tfp/timetracking/outbox/**`

## Archivos que debe actualizar

- `docs/integration/event-catalog.md` si cambia el contrato de algún
  mensaje.
- `docs/adr/ADR-0005-transactional-outbox-sin-broker.md` únicamente si se
  registra una decisión nueva (no se reescribe un ADR aceptado; se crea uno
  nuevo que lo referencia).
- `tasks/_reports/TXXX-report.md`, sección "Riesgos detectados" si se
  identifica una limitación del mecanismo actual.
