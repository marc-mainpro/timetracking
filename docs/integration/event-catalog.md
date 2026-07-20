# Catálogo de eventos de integración

Catálogo completo (T704) de los eventos de integración publicados por el
Transactional Outbox (ADR-0005). Para cada tipo: nombre y versión,
descripción, disparador de negocio, esquema del envelope/payload con
ejemplo, semántica de entrega y notas de idempotencia. El ciclo de vida del
publicador (polling, backoff, reintentos, archivado) está documentado por
separado en `docs/integration/outbox-publisher.md` (T703); este documento se
centra en el **contrato** que ven los consumidores, no en cómo se entrega.

## Envelope

Todos los eventos de integración comparten el mismo envelope:

```json
{
  "eventId": "uuid",
  "eventType": "time-tracking.workday-closed.v1",
  "eventVersion": 1,
  "occurredAt": "ISO-8601 UTC",
  "tenantId": "uuid",
  "aggregateId": "uuid",
  "payload": {}
}
```

| Campo         | Tipo               | Descripción                                                                                                   |
| ------------- | ------------------ | --------------------------------------------------------------------------------------------------------------- |
| `eventId`     | `string` (UUID)    | Identificador único y estable del evento. Coincide con el `id` de la fila de `outbox_message` que lo originó. Es la clave de deduplicación para consumidores idempotentes. |
| `eventType`   | `string`           | Nombre versionado, convención `dominio.hecho.vN` (ver "Tipos" más abajo).                                        |
| `eventVersion`| `integer`          | Versión numérica del esquema del `payload` (parte numérica de `eventType`, duplicada para uso programático).    |
| `occurredAt`  | `string` (ISO-8601)| Instante en el que ocurrió el hecho de negocio (reloj de dominio), no el instante en que se publicó.             |
| `tenantId`    | `string` (UUID)    | Tenant al que pertenece el evento. Puede diferir del tenant "actual" de la petición HTTP que lo originó (p.ej. `tenant.registered.v1` se emite en un endpoint público sin JWT). |
| `aggregateId` | `string` (UUID)    | Identificador del agregado de dominio que originó el evento.                                                     |
| `payload`     | `object`           | Cuerpo específico del tipo de evento (ver cada sección). Nunca contiene entidades JPA ni modelos internos, solo ids, cadenas, instantes y colecciones simples ya serializables. |

**Nota de implementación:** internamente, `shared.domain.IntegrationEvent`
añade un campo `aggregateType` (p.ej. `"Workday"`, `"Tenant"`, `"Employee"`,
`"CorrectionRequest"`) que **no** forma parte del envelope público de
arriba; alimenta la columna `aggregate_type` de `outbox_message`, usada
internamente por el publicador para logging, no por los consumidores
externos.

## Semántica de entrega

**At-least-once real** (no solo documentada, ver `docs/integration/outbox-publisher.md`,
T703): el publicador reclama los mensajes `outbox_message` por polling,
reintenta con backoff exponencial + jitter ante fallos, y solo marca
`PUBLISHED` tras una entrega exitosa al puerto `IntegrationEventPublisher`.
Esto implica:

- El **mismo** `eventId` puede llegar más de una vez a un consumidor: un
  fallo de red/proceso entre "el consumidor procesó el evento" y "el
  publicador se enteró de que tuvo éxito" produce un reintento del mismo
  mensaje.
- Los eventos de un mismo agregado se publican en el orden en que se
  reclaman (aproximadamente el orden de escritura), pero **no hay garantía
  fuerte de orden total** entre mensajes de agregados distintos ni frente a
  reintentos que adelantan a mensajes más nuevos que aún no vencieron su
  backoff. Un consumidor que necesite orden estricto por agregado debe
  usar `occurredAt` (o una versión del agregado, si el evento la incluyera)
  para reordenar del lado del consumidor.
- No hay garantía de entrega exactamente una vez ni de baja latencia
  (polling, no push): ver ADR-0005 y `outbox-publisher.md` para el
  intervalo de polling por defecto.

## Idempotencia de consumidores (obligatoria)

Todo consumidor de estos eventos **debe** deduplicar por `eventId`: es la
única forma de obtener semántica "efectivamente una vez" sobre un canal
at-least-once. El patrón de referencia recomendado (y demostrado de extremo
a extremo por T704) es:

1. Mantener una tabla propia del consumidor con `event_id` como clave
   primaria (o única) de los eventos ya procesados.
2. Antes de aplicar el efecto de negocio del evento, comprobar si
   `event_id` ya existe; si existe, ignorar el evento (ya se procesó).
3. Si no existe, aplicar el efecto e insertar la marca de "procesado" en la
   **misma transacción** que el efecto (para que ambos ocurran atómicamente
   o ninguno).
4. Si la inserción falla por violación de la clave primaria (dos hilos/
   instancias procesando el mismo evento a la vez), tratarlo igual que un
   duplicado: no es un error, es la red de seguridad de la concurrencia.

Este backend incluye un **consumidor de ejemplo interno** que implementa
exactamente este patrón, únicamente con fines de demostración (no es un
caso de uso de negocio real):

- Tabla `processed_event(event_id UUID PK, processed_at TIMESTAMPTZ NOT NULL)`
  (migración `V9__processed_event.sql`).
- `com.tfp.timetracking.outbox.infrastructure.demo.DemoIdempotentEventConsumer`,
  enganchado como `IntegrationEventListener` a `LoggingIntegrationEventPublisher`
  (el único "sink" real que permite ADR-0005 en el MVP: un log estructurado).
- Prueba de extremo a extremo:
  `OutboxGuaranteesIntegrationTest` (`backend/src/test/java/com/tfp/timetracking/outbox/`),
  que cierra una jornada real, deja el mensaje `PENDING` en el outbox (misma
  transacción que el negocio), lo publica con `PublishPendingOutboxMessages`,
  observa que el consumidor de demostración lo procesa una vez, y luego
  fuerza una redelivery deliberada del mismo evento para comprobar que no
  se duplica ningún efecto.

Cualquier consumidor real futuro (un servicio externo, otro módulo, etc.)
debería seguir el mismo patrón con su propia tabla de deduplicación; no debe
reutilizar `processed_event`, que es exclusiva de la demostración.

## Política de versionado

- Un cambio **compatible hacia atrás** del `payload` (añadir un campo
  opcional nuevo que los consumidores existentes puedan ignorar) se hace
  sin cambiar `eventType` ni `eventVersion`.
- Un cambio **incompatible** (eliminar/renombrar un campo, cambiar su tipo o
  semántica, cambiar qué dispara el evento) **siempre** se publica como un
  tipo nuevo con versión incrementada (p.ej. `time-tracking.workday-closed.v2`).
- **Nunca se muta el esquema de un `.v1` (o cualquier versión) ya
  publicado.** Los tipos y versiones antiguos se mantienen mientras existan
  consumidores que los necesiten; retirarlos requiere coordinación explícita
  fuera del alcance de este documento (no hay mecanismo de "deprecation"
  automático en el MVP).
- `eventVersion` (entero) siempre coincide con el sufijo `vN` de
  `eventType`; ambos viajan en el envelope para que un consumidor pueda
  enrutar por `eventType` completo o, si lo prefiere, por
  tipo-base + `eventVersion` por separado.

## Tipos de evento

### `tenant.registered.v1`

- **Módulo productor:** `tenant` (`tenant.application.integration.TenantIntegrationEventMapper`).
- **Disparador de negocio:** alta de un tenant nuevo (registro público,
  `POST /api/v1/auth/register`), junto con su usuario administrador inicial
  (que además dispara `identity.employee-created.v1` en la misma operación).
- **`aggregateId`:** id del tenant creado.

```json
{
  "eventId": "8f14e45f-ceea-4e6e-a2f4-2f6f7b7b1a10",
  "eventType": "tenant.registered.v1",
  "eventVersion": 1,
  "occurredAt": "2026-07-20T09:00:00Z",
  "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "aggregateId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "payload": {
    "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
    "name": "Acme Corp",
    "timezone": "Europe/Madrid"
  }
}
```

| `payload`  | Tipo   | Descripción                          |
| ---------- | ------ | ------------------------------------- |
| `tenantId` | UUID   | Igual que `aggregateId`.              |
| `name`     | string | Nombre del tenant en el momento del alta. |
| `timezone` | string | Zona horaria IANA configurada para el tenant. |

- **Idempotencia:** un consumidor que provisione recursos externos al
  recibir este evento (p.ej. crear un espacio de trabajo en otro sistema)
  debe usar `eventId` (o, si necesita idempotencia por tenant en vez de por
  evento, `tenantId`, que es estable y único por tenant) para no duplicar el
  alta ante una redelivery.

### `identity.employee-created.v1`

- **Módulo productor:** `identity` (`identity.application.integration.IdentityIntegrationEventMapper`).
- **Disparador de negocio:** alta de un empleado (incluye al usuario
  administrador creado durante el registro del tenant, y a cualquier
  empleado dado de alta después por un `TENANT_ADMIN`).
- **`aggregateId`:** id del usuario/empleado (agregado `identity.domain.User`,
  expuesto en el contrato con el término de negocio "empleado").

```json
{
  "eventId": "2b1f9e2a-8c3e-4b9a-9d3a-6a2b6e1f9c02",
  "eventType": "identity.employee-created.v1",
  "eventVersion": 1,
  "occurredAt": "2026-07-20T09:00:00.150Z",
  "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "aggregateId": "9aa9e0d0-8d5f-40e4-a210-091620476f65",
  "payload": {
    "employeeId": "9aa9e0d0-8d5f-40e4-a210-091620476f65",
    "email": "jane.doe@acme.test",
    "roles": ["TENANT_ADMIN"]
  }
}
```

| `payload`    | Tipo             | Descripción                                                    |
| ------------ | ---------------- | ---------------------------------------------------------------- |
| `employeeId` | UUID             | Igual que `aggregateId`.                                        |
| `email`      | string           | Email normalizado del empleado en el momento de la creación.     |
| `roles`      | array de string  | Roles asignados en el momento de la creación: `"TENANT_ADMIN"` y/o `"EMPLOYEE"` (`identity.domain.Role`). |

- **Idempotencia:** típico caso de aprovisionamiento externo (p.ej. crear
  una cuenta en un sistema de nóminas): deduplicar por `eventId` antes de
  crear el recurso externo evita cuentas duplicadas ante redelivery.

### `identity.employee-deactivated.v1`

- **Módulo productor:** `identity`.
- **Disparador de negocio:** desactivación de un empleado por un
  `TENANT_ADMIN`.
- **`aggregateId`:** id del usuario/empleado desactivado.

```json
{
  "eventId": "0c9b6a63-2f77-4c53-8c1a-5a2b9e4d7f31",
  "eventType": "identity.employee-deactivated.v1",
  "eventVersion": 1,
  "occurredAt": "2026-07-20T10:15:00Z",
  "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "aggregateId": "9aa9e0d0-8d5f-40e4-a210-091620476f65",
  "payload": {
    "employeeId": "9aa9e0d0-8d5f-40e4-a210-091620476f65"
  }
}
```

| `payload`    | Tipo | Descripción              |
| ------------ | ---- | ------------------------- |
| `employeeId` | UUID | Igual que `aggregateId`. |

- **Idempotencia:** deduplicar por `eventId` evita, por ejemplo, enviar dos
  veces una notificación de "acceso revocado" al sistema externo ante una
  redelivery.

### `time-tracking.workday-started.v1`

- **Módulo productor:** `timetracking` (`timetracking.application.integration.TimeTrackingIntegrationEventMapper`).
- **Disparador de negocio:** un empleado inicia su jornada
  (`POST /api/v1/workdays/start`).
- **`aggregateId`:** id de la jornada (`Workday`).

```json
{
  "eventId": "604445b1-297c-4866-856c-f6ecc69e2b5a",
  "eventType": "time-tracking.workday-started.v1",
  "eventVersion": 1,
  "occurredAt": "2026-07-20T08:00:00Z",
  "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "aggregateId": "6af6b583-d83e-40fa-a842-e5bce5b64e5f",
  "payload": {
    "workdayId": "6af6b583-d83e-40fa-a842-e5bce5b64e5f",
    "employeeId": "9aa9e0d0-8d5f-40e4-a210-091620476f65",
    "startedAt": "2026-07-20T08:00:00Z"
  }
}
```

| `payload`    | Tipo              | Descripción                          |
| ------------ | ----------------- | -------------------------------------- |
| `workdayId`  | UUID              | Igual que `aggregateId`.               |
| `employeeId` | UUID              | Empleado dueño de la jornada.          |
| `startedAt`  | string (ISO-8601) | Instante de inicio de la jornada.      |

- **Idempotencia:** deduplicar por `eventId` evita, por ejemplo, contar dos
  veces el inicio de jornada en un sistema externo de asistencia.

### `time-tracking.workday-closed.v1`

- **Módulo productor:** `timetracking`.
- **Disparador de negocio:** un empleado cierra su jornada activa
  (`POST /api/v1/workdays/current/end`).
- **`aggregateId`:** id de la jornada (`Workday`).

```json
{
  "eventId": "d6c875d7-2df5-4276-bd6f-6eff512e8fa1",
  "eventType": "time-tracking.workday-closed.v1",
  "eventVersion": 1,
  "occurredAt": "2026-07-20T17:00:00Z",
  "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "aggregateId": "6af6b583-d83e-40fa-a842-e5bce5b64e5f",
  "payload": {
    "workdayId": "6af6b583-d83e-40fa-a842-e5bce5b64e5f",
    "employeeId": "9aa9e0d0-8d5f-40e4-a210-091620476f65",
    "startedAt": "2026-07-20T08:00:00Z",
    "endedAt": "2026-07-20T17:00:00Z"
  }
}
```

| `payload`    | Tipo              | Descripción                        |
| ------------ | ----------------- | ------------------------------------ |
| `workdayId`  | UUID              | Igual que `aggregateId`.             |
| `employeeId` | UUID              | Empleado dueño de la jornada.        |
| `startedAt`  | string (ISO-8601) | Instante de inicio de la jornada.    |
| `endedAt`    | string (ISO-8601) | Instante de cierre de la jornada.    |

- **Este es el evento de referencia** usado por las pruebas de extremo a
  extremo de T702/T704 (`EndWorkdayUseCaseAtomicityIntegrationTest`,
  `OutboxGuaranteesIntegrationTest`): demuestra la atomicidad negocio+outbox
  y el flujo completo negocio → outbox → publicador → consumidor idempotente.
- **Idempotencia:** deduplicar por `eventId` es crítico aquí: un consumidor
  que calculase horas trabajadas o nóminas a partir de este evento
  duplicaría el cómputo de una jornada ante una redelivery si no
  deduplicara.

### `corrections.correction-requested.v1`

- **Módulo productor:** `corrections` (`corrections.application.integration.CorrectionsIntegrationEventMapper`).
- **Disparador de negocio:** un empleado solicita una corrección sobre una
  jornada ya cerrada.
- **`aggregateId`:** id de la solicitud de corrección (`CorrectionRequest`).

```json
{
  "eventId": "1a2b3c4d-5e6f-4789-90ab-cdef01234567",
  "eventType": "corrections.correction-requested.v1",
  "eventVersion": 1,
  "occurredAt": "2026-07-20T18:00:00Z",
  "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "aggregateId": "b2c3d4e5-f607-4891-a2b3-c4d5e6f70819",
  "payload": {
    "correctionId": "b2c3d4e5-f607-4891-a2b3-c4d5e6f70819",
    "workdayId": "6af6b583-d83e-40fa-a842-e5bce5b64e5f",
    "requestedBy": "9aa9e0d0-8d5f-40e4-a210-091620476f65"
  }
}
```

| `payload`      | Tipo | Descripción                                   |
| -------------- | ---- | ----------------------------------------------- |
| `correctionId` | UUID | Igual que `aggregateId`.                        |
| `workdayId`    | UUID | Jornada sobre la que se solicita la corrección. |
| `requestedBy`  | UUID | Empleado que solicitó la corrección.            |

- **Idempotencia:** deduplicar por `eventId` evita, por ejemplo, notificar
  dos veces a un `TENANT_ADMIN` de la misma solicitud pendiente.

### `corrections.correction-approved.v1`

- **Módulo productor:** `corrections`.
- **Disparador de negocio:** un `TENANT_ADMIN` aprueba una solicitud de
  corrección.
- **`aggregateId`:** id de la solicitud de corrección.

```json
{
  "eventId": "2b3c4d5e-6f70-4891-a2b3-c4d5e6f70820",
  "eventType": "corrections.correction-approved.v1",
  "eventVersion": 1,
  "occurredAt": "2026-07-21T09:00:00Z",
  "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "aggregateId": "b2c3d4e5-f607-4891-a2b3-c4d5e6f70819",
  "payload": {
    "correctionId": "b2c3d4e5-f607-4891-a2b3-c4d5e6f70819",
    "workdayId": "6af6b583-d83e-40fa-a842-e5bce5b64e5f",
    "resolvedBy": "5d6e7f80-9192-4a3b-8c4d-5e6f70819293"
  }
}
```

| `payload`      | Tipo | Descripción                                     |
| -------------- | ---- | -------------------------------------------------- |
| `correctionId` | UUID | Igual que `aggregateId`.                           |
| `workdayId`    | UUID | Jornada afectada por la corrección aprobada.       |
| `resolvedBy`   | UUID | `TENANT_ADMIN` que aprobó la solicitud.            |

- **Idempotencia:** deduplicar por `eventId` evita aplicar dos veces el
  ajuste resultante sobre un sistema externo de nóminas/reporting.

### `corrections.correction-rejected.v1`

- **Módulo productor:** `corrections`.
- **Disparador de negocio:** un `TENANT_ADMIN` rechaza una solicitud de
  corrección.
- **`aggregateId`:** id de la solicitud de corrección.

```json
{
  "eventId": "3c4d5e6f-7081-4922-b3c4-d5e6f7081930",
  "eventType": "corrections.correction-rejected.v1",
  "eventVersion": 1,
  "occurredAt": "2026-07-21T09:05:00Z",
  "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "aggregateId": "b2c3d4e5-f607-4891-a2b3-c4d5e6f70819",
  "payload": {
    "correctionId": "b2c3d4e5-f607-4891-a2b3-c4d5e6f70819",
    "workdayId": "6af6b583-d83e-40fa-a842-e5bce5b64e5f",
    "resolvedBy": "5d6e7f80-9192-4a3b-8c4d-5e6f70819293"
  }
}
```

| `payload`      | Tipo | Descripción                                  |
| -------------- | ---- | ----------------------------------------------- |
| `correctionId` | UUID | Igual que `aggregateId`.                        |
| `workdayId`    | UUID | Jornada afectada por la corrección rechazada.   |
| `resolvedBy`   | UUID | `TENANT_ADMIN` que rechazó la solicitud.        |

- **Idempotencia:** igual que en `.approved`, deduplicar por `eventId` evita
  notificaciones o efectos duplicados en sistemas externos.

## Eventos de dominio sin traducción a integración

Decisión de alcance del MVP (T702, reafirmada en T704): `BreakStarted` /
`BreakEnded` (módulo `timetracking`) **no** se traducen a evento de
integración. Son eventos de grano fino de interés solo interno
(auditoría/consistencia del agregado `Workday`); ningún tipo de este
catálogo los necesita, y publicarlos ampliaría innecesariamente la
superficie de contrato externo. Si en el futuro un consumidor real los
necesita, se añadirán como `time-tracking.break-started.v1` /
`time-tracking.break-ended.v1` sin romper compatibilidad con los tipos ya
publicados (ver "Política de versionado"). Ver
`timetracking.application.integration.TimeTrackingIntegrationEventMapper`.

## Reglas de implementación

- Los eventos de integración se escriben en `outbox_message` en la misma
  transacción que el cambio de negocio (Transactional Outbox, ADR-0005): un
  mapper por módulo (`*/application/integration/`) traduce el evento de
  dominio a `IntegrationEvent`, y `OutboxDomainEventPublisher`
  (`outbox.infrastructure`) lo persiste vía el puerto `OutboxWriter` dentro
  de la misma transacción `@Transactional` del caso de uso.
- Nunca se publican entidades JPA ni modelos internos.
- El publicador (T703, `docs/integration/outbox-publisher.md`) reclama los
  mensajes `outbox_message` por polling, reintenta con backoff exponencial
  ante fallos y solo marca `PUBLISHED` tras una entrega exitosa.
- Todo consumidor debe ser idempotente por `eventId` (ver "Idempotencia de
  consumidores" arriba).
