# Skill: create-domain-event

## Objetivo

Modelar un nuevo evento de dominio como hecho pasado e inmutable, generado
por un agregado, sin dependencias de framework.

## Entradas

- Agregado que origina el evento (Tenant, User, Workday, CorrectionRequest).
- Catálogo actual de eventos de dominio
  (`tasks/_context/CONTEXT-DOMINIO.md` §3).

## Pasos

1. Definir el evento como un `record` Java inmutable dentro de
   `<módulo>/domain`, sin anotaciones de Spring ni de JPA.
2. Incluir los campos comunes: `eventId` (UUID), `occurredAt` (Instant),
   `tenantId`, `aggregateId`, más los datos propios mínimos del hecho.
3. Generar el evento **dentro del método del agregado** que produce la
   transición de estado correspondiente (nunca en el caso de uso ni en el
   controlador), como parte de la lista de eventos pendientes del agregado.
4. En el caso de uso de `application`, recoger los eventos generados
   **después de persistir** el agregado y entregarlos al mecanismo de
   publicación (dominio interno / outbox, según corresponda).
5. No usar el evento para imponer invariantes: las invariantes se validan de
   forma síncrona en el propio método del agregado antes de emitir el
   evento.

## Validaciones

- El evento no importa `org.springframework.*` ni `jakarta.persistence.*`.
- El evento es inmutable (record, sin setters).
- El evento representa un hecho ya ocurrido (nombre en pasado:
  `WorkdayStarted`, no `StartWorkday`).
- El evento no se usa para decidir invariantes de otro agregado de forma
  síncrona (eso sería lógica de negocio oculta en un listener).

## Pruebas

- Test unitario del agregado que verifica que, tras la operación de
  dominio correspondiente, el evento esperado se genera con los datos
  correctos (`tenantId`, `aggregateId`, campos propios).
- Test que verifica que NO se genera el evento cuando la operación falla por
  una invariante violada.

## Criterios de finalización

- El evento está definido, se genera correctamente y tiene test unitario.
- `docs/domain/reglas-de-negocio.md`/dominio referencian el nuevo evento si
  corresponde.

## Archivos que puede modificar

- `backend/src/main/java/com/tfp/timetracking/<módulo>/domain/**`
- `backend/src/test/java/com/tfp/timetracking/<módulo>/domain/**`

## Archivos que debe actualizar

- `docs/domain/agregados.md` o `docs/domain/reglas-de-negocio.md` si el
  evento documenta una transición nueva.
- `docs/integration/event-catalog.md` NO se toca aquí si el evento no tiene
  aún una versión de integración asociada (ver skill
  `create-integration-event`).
