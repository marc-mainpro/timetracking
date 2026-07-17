# Skill: create-integration-event

## Objetivo

Mapear un evento de dominio a un evento de integración versionado, publicado
vía Transactional Outbox, sin exponer entidades JPA ni modelos internos.

## Entradas

- Evento de dominio ya existente (ver skill `create-domain-event`).
- Tipo de evento de integración a crear (ver
  `tasks/_context/CONTEXT-DOMINIO.md` §4 y
  `docs/integration/event-catalog.md`).

## Pasos

1. Definir el `eventType` siguiendo la convención
   `<contexto>.<hecho-en-kebab-case>.v<N>` (p. ej.
   `time-tracking.workday-closed.v1`).
2. Construir el `payload` a partir de los datos del evento de dominio,
   nunca a partir de la entidad JPA ni de un DTO de API existente; incluir
   solo los datos necesarios para los consumidores.
3. Envolver el payload en el envelope estándar: `eventId`, `eventType`,
   `eventVersion`, `occurredAt`, `tenantId`, `aggregateId`, `payload`.
4. En el caso de uso que persiste el cambio de negocio, insertar el mensaje
   en `outbox_message` **en la misma transacción** (mismo método
   `@Transactional`) que el cambio de negocio.
5. Si el contrato de un evento existente cambia de forma incompatible, crear
   una nueva versión (`v2`) en lugar de modificar la anterior; mantener
   ambas mientras existan consumidores de la versión antigua.

## Validaciones

- El payload no contiene campos internos (IDs técnicos de infraestructura,
  entidades JPA serializadas, contraseñas, tokens).
- El `eventType` y su versión están documentados en
  `docs/integration/event-catalog.md` antes de darse por completado.
- La escritura en `outbox_message` ocurre en la misma transacción que el
  cambio de negocio (no hay una llamada asíncrona separada).

## Pruebas

- Test de integración con Testcontainers que verifica: si la transacción de
  negocio falla, no se persiste el mensaje en `outbox_message` (atomicidad).
- Test que verifica el contenido exacto del envelope y del payload
  publicado para el evento.
- Test de idempotencia del lado consumidor si existe un consumidor real en
  el módulo.

## Criterios de finalización

- El evento de integración está documentado en el catálogo, con ejemplo de
  payload.
- Tests de atomicidad en verde.

## Archivos que puede modificar

- `backend/src/main/java/com/tfp/timetracking/outbox/**`
- `backend/src/main/java/com/tfp/timetracking/<módulo>/application/**`
  (punto de publicación)
- `backend/src/test/java/com/tfp/timetracking/**`

## Archivos que debe actualizar

- `docs/integration/event-catalog.md` (obligatorio).
- `docs/adr/` con un nuevo ADR si se introduce un tipo de transporte distinto
  al Outbox actual (ver ADR-0005).
