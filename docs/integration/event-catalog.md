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

### `tenant.activated.v1`, `tenant.suspended.v1`, `tenant.reactivated.v1`, `tenant.archived.v1`

- **Módulo productor:** `tenant` (`TenantIntegrationEventMapper`).
- **Disparador de negocio:** transiciones del ciclo de vida del tenant
  ejecutadas por un `PLATFORM_ADMIN` desde la API de plataforma
  (`POST /api/v1/platform/tenants/{id}/{activate|suspend|reactivate|archive}`,
  ADR-0010). Cada transición valida el estado de origen en el agregado
  `Tenant` y, tras persistir, emite el evento correspondiente vía Outbox.
- **`aggregateId`:** id del tenant afectado (igual que `tenantId`).

```json
{
  "eventId": "9c1e0b2a-2d3f-4a5b-8c7d-1e2f3a4b5c6d",
  "eventType": "tenant.suspended.v1",
  "eventVersion": 1,
  "occurredAt": "2026-07-24T10:00:00Z",
  "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "aggregateId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "payload": {
    "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
    "reason": "Impago reiterado"
  }
}
```

| `payload`  | Tipo   | Descripción                                                        |
| ---------- | ------ | ------------------------------------------------------------------ |
| `tenantId` | UUID   | Igual que `aggregateId`.                                           |
| `reason`   | string | Solo en `suspended` (obligatorio) y `archived` (opcional). Ausente en `activated`/`reactivated`. |

- **Idempotencia:** un consumidor que reaccione a la suspensión/archivado
  (p.ej. cortar accesos externos) debe deduplicar por `eventId`. El estado
  operativo autoritativo es siempre el del propio tenant; el evento es una
  notificación, no la fuente de verdad.

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

### `tenant.registration-requested.v1`

- **Módulo productor:** `tenant` (`tenant.application.integration.TenantIntegrationEventMapper`).
- **Disparador de negocio:** alguien envía el formulario público de alta
  (`POST /api/v1/public/tenant-registrations`, RF-REG-001). **Todavía no existe
  ningún tenant**: solo una solicitud pendiente de verificar el correo.
- **`tenantId`:** el tenant de plataforma
  (`00000000-0000-0000-0000-000000000001`). Una solicitud es anterior al tenant,
  así que no hay otro al que atribuirla.
- **`aggregateId`:** id de la solicitud (`tenant_registration.id`).

```json
{
  "eventId": "0d4f8a2c-3b91-4d67-8f2e-1a6b9c0d3e45",
  "eventType": "tenant.registration-requested.v1",
  "eventVersion": 1,
  "occurredAt": "2026-08-01T10:00:00Z",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "aggregateId": "7c1e2f30-4a5b-46c7-8d9e-0f1a2b3c4d5e",
  "payload": {
    "registrationId": "7c1e2f30-4a5b-46c7-8d9e-0f1a2b3c4d5e",
    "companyName": "Acme Corp",
    "email": "owner@acme.test",
    "source": "PUBLIC_WEB"
### `calendar.calendar-created.v1`, `calendar.calendar-updated.v1`

- **Módulo productor:** `calendar` (`calendar.application.integration.CalendarIntegrationEventMapper`).
- **Disparador de negocio:** un `TENANT_ADMIN` crea o edita un calendario
  laboral (`POST` / `PUT /api/v1/admin/calendars`, T70-04, ADR-0017).
- **`aggregateId`:** id del calendario.

```json
{
  "eventId": "4d5e6f70-8192-4a3b-8c4d-5e6f70819304",
  "eventType": "calendar.calendar-created.v1",
  "eventVersion": 1,
  "occurredAt": "2026-08-04T09:00:00Z",
  "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "aggregateId": "7e8f9012-3456-4789-a012-3456789abcde",
  "payload": {
    "calendarId": "7e8f9012-3456-4789-a012-3456789abcde",
    "name": "Calendario general",
    "timezone": "Europe/Madrid",
    "validFrom": "2026-01-01",
    "validTo": "2026-12-31"
  }
}
```

| `payload`        | Tipo   | Descripción                                            |
| ---------------- | ------ | ------------------------------------------------------ |
| `registrationId` | UUID   | Igual que `aggregateId`.                               |
| `companyName`    | string | Nombre de la organización solicitada.                  |
| `email`          | string | Correo del propietario, normalizado a minúsculas.      |
| `source`         | string | Canal de entrada de la solicitud (`PUBLIC_WEB`).       |

- **Idempotencia:** deduplicar por `eventId`. Un consumidor de métricas o
  antifraude no debe contar dos veces la misma solicitud ante una redelivery.

### `tenant.registration-verification-requested.v1`

- **Módulo productor:** `tenant` (`tenant.application.integration.TenantIntegrationEventMapper`).
- **Disparador de negocio:** hay que hacer llegar al solicitante un token de
  verificación, ya sea en el alta inicial o en un reenvío (RF-REG-004).
- **Consumidor:** `tenant.infrastructure.TenantRegistrationEmailListener`, que
  invoca el puerto `EmailSender` fuera de la transacción de negocio (ADR-0012).
- **`aggregateId`:** id de la solicitud.

> **Contiene un secreto.** `verificationToken` es el token en claro, y es el
> único campo de todo este catálogo que lo es. Existe porque el consumidor de
> correo necesita construir el enlace, y por eso va en un evento distinto del
> que describe el hecho de negocio. El publicador nunca registra el payload en
> el log (solo el sobre), el token caduca en 24 h y es de un solo uso, pero la
> fila de `outbox_message` lo contiene hasta que el archivador la purga: ver la
> sección de consecuencias de ADR-0016.

```json
{
  "eventId": "1e5f9b3d-4c02-4e78-9a3f-2b7c0d1e4f56",
  "eventType": "tenant.registration-verification-requested.v1",
  "eventVersion": 1,
  "occurredAt": "2026-08-01T10:00:00Z",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "aggregateId": "7c1e2f30-4a5b-46c7-8d9e-0f1a2b3c4d5e",
  "payload": {
    "registrationId": "7c1e2f30-4a5b-46c7-8d9e-0f1a2b3c4d5e",
    "email": "owner@acme.test",
    "ownerFirstName": "Ana",
    "verificationToken": "8Zt3xQ9pR1sV7kL0mN4bC6dE2fG5hJ8yA1uW3iO5qS0",
    "expiresAt": "2026-08-02T10:00:00Z",
    "resend": false
| `payload`    | Tipo   | Descripción                                                        |
| ------------ | ------ | ------------------------------------------------------------------ |
| `calendarId` | UUID   | Igual que `aggregateId`.                                           |
| `name`       | string | Nombre del calendario, único dentro del tenant.                    |
| `timezone`   | string | Zona horaria IANA del calendario (RF-CAL-007).                     |
| `validFrom`  | string | Inicio de vigencia como fecha local `YYYY-MM-DD`, **no** instante. |
| `validTo`    | string | Fin de vigencia inclusivo. **Ausente** si la vigencia es indefinida. |

- **Fechas locales, no instantes:** la vigencia de un calendario es un periodo
  del calendario civil (RNF-011). Serializarla como `Instant` obligaría a elegir
  una hora arbitraria y haría que el mismo calendario «cambiara de día» según la
  zona del consumidor.
- **El payload es la cabecera, no el detalle:** reglas semanales, festivos y
  jornadas especiales no viajan en el evento. Un consumidor que necesite el
  detalle debe releerlo por la API. Mantener el contrato externo pequeño evita
  versionarlo cada vez que cambie la estructura interna del calendario.
- **Idempotencia:** deduplicar por `eventId`. `.updated` es un hecho de
  reemplazo completo, así que reprocesarlo es inocuo si el consumidor guarda la
  última versión leída.

### `calendar.calendar-archived.v1`

- **Módulo productor:** `calendar` (`CalendarIntegrationEventMapper`).
- **Disparador de negocio:** un `TENANT_ADMIN` archiva un calendario
  (`DELETE /api/v1/admin/calendars/{id}`, borrado lógico). A partir de ese
  momento el calendario deja de participar en la resolución del calendario
  efectivo, y los empleados que lo tuvieran asignado pasan a resolver al ámbito
  menos específico que sí tenga calendario disponible.
- **`aggregateId`:** id del calendario.

```json
{
  "eventId": "5e6f7081-9243-4b5c-8d6e-7f8091a2b3c4",
  "eventType": "calendar.calendar-archived.v1",
  "eventVersion": 1,
  "occurredAt": "2026-08-04T10:00:00Z",
  "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "aggregateId": "7e8f9012-3456-4789-a012-3456789abcde",
  "payload": {
    "calendarId": "7e8f9012-3456-4789-a012-3456789abcde",
    "name": "Calendario general"
  }
}
```

| `payload`           | Tipo    | Descripción                                                  |
| ------------------- | ------- | ------------------------------------------------------------ |
| `registrationId`    | UUID    | Igual que `aggregateId`.                                     |
| `email`             | string  | Destinatario del correo de verificación.                     |
| `ownerFirstName`    | string  | Nombre del propietario, para personalizar el mensaje.        |
| `verificationToken` | string  | Token en claro, un solo uso. **Nunca debe registrarse.**      |
| `expiresAt`         | Instant | Caducidad del token.                                         |
| `resend`            | boolean | `true` si es un reenvío y no el envío inicial.               |

- **Idempotencia:** deduplicar por `eventId`. Reenviar dos veces el mismo correo
  es molesto pero inocuo; el token no cambia por reprocesar el evento.

### `tenant.registration-email-verified.v1`

- **Módulo productor:** `tenant` (`tenant.application.integration.TenantIntegrationEventMapper`).
- **Disparador de negocio:** el solicitante demuestra que controla el correo
  (`POST /api/v1/public/tenant-registrations/verify-email`) y la solicitud pasa a
  `PENDING_REVIEW`.
- **`aggregateId`:** id de la solicitud.

```json
{
  "eventId": "2f60ac4e-5d13-4f89-ab40-3c8d1e2f5061",
  "eventType": "tenant.registration-email-verified.v1",
  "eventVersion": 1,
  "occurredAt": "2026-08-01T10:05:00Z",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "aggregateId": "7c1e2f30-4a5b-46c7-8d9e-0f1a2b3c4d5e",
  "payload": {
    "registrationId": "7c1e2f30-4a5b-46c7-8d9e-0f1a2b3c4d5e",
    "email": "owner@acme.test"
| `payload`    | Tipo   | Descripción              |
| ------------ | ------ | ------------------------ |
| `calendarId` | UUID   | Igual que `aggregateId`. |
| `name`       | string | Nombre en el momento de archivarlo. |

- **Idempotencia:** archivar es idempotente por naturaleza (el agregado rechaza
  un segundo archivado con `CALENDAR_ARCHIVED`), pero el consumidor debe
  deduplicar igualmente por `eventId` ante una redelivery.

### `calendar.calendar-assigned.v1`, `calendar.calendar-assignment-removed.v1`

- **Módulo productor:** `calendar` (`CalendarIntegrationEventMapper`).
- **Disparador de negocio:** un `TENANT_ADMIN` asigna un calendario a un ámbito
  o retira la asignación (`POST` / `DELETE /api/v1/admin/calendar-assignments`,
  RF-CAL-006).
- **`aggregateId`:** id de la **asignación**; el calendario afectado va en
  `payload.calendarId`.

```json
{
  "eventId": "6f708192-a3b4-4c5d-8e6f-708192a3b4c5",
  "eventType": "calendar.calendar-assigned.v1",
  "eventVersion": 1,
  "occurredAt": "2026-08-04T11:00:00Z",
  "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
  "aggregateId": "8f901234-5678-4901-b234-56789abcdef0",
  "payload": {
    "assignmentId": "8f901234-5678-4901-b234-56789abcdef0",
    "calendarId": "7e8f9012-3456-4789-a012-3456789abcde",
    "scope": "EMPLOYEE",
    "targetId": "5d6e7f80-9192-4a3b-8c4d-5e6f70819293"
  }
}
```

| `payload`        | Tipo   | Descripción                       |
| ---------------- | ------ | --------------------------------- |
| `registrationId` | UUID   | Igual que `aggregateId`.          |
| `email`          | string | Correo verificado.                |

- **Idempotencia:** deduplicar por `eventId`. Un consumidor que avise a
  plataforma de que hay trabajo en la bandeja no debe notificar dos veces.

### `tenant.registration-approved.v1`

- **Módulo productor:** `tenant` (`tenant.application.integration.TenantIntegrationEventMapper`).
- **Disparador de negocio:** un `PLATFORM_ADMIN` aprueba la solicitud
  (`POST /api/v1/platform/registrations/{id}/approve`). En la misma transacción
  se crean el tenant —**en estado `PENDING`, no `ACTIVE`**— y su primer
  `TENANT_ADMIN`, que dispara además `identity.employee-created.v1`.
- **`aggregateId`:** id de la solicitud, no del tenant creado.

```json
{
  "eventId": "3a71bd5f-6e24-4a90-bc51-4d9e2f306172",
  "eventType": "tenant.registration-approved.v1",
  "eventVersion": 1,
  "occurredAt": "2026-08-01T11:00:00Z",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "aggregateId": "7c1e2f30-4a5b-46c7-8d9e-0f1a2b3c4d5e",
  "payload": {
    "registrationId": "7c1e2f30-4a5b-46c7-8d9e-0f1a2b3c4d5e",
    "tenantId": "3fbb6f1e-1c7c-4a52-9e64-5f4a6b0d2c11",
    "ownerUserId": "9b8a7c6d-5e4f-4302-9182-736455647382"
  }
}
```

| `payload`        | Tipo | Descripción                                                        |
| ---------------- | ---- | ------------------------------------------------------------------ |
| `registrationId` | UUID | Igual que `aggregateId`.                                           |
| `tenantId`       | UUID | Tenant creado, en estado `PENDING`. No coincide con el del sobre.  |
| `ownerUserId`    | UUID | Primer `TENANT_ADMIN` de ese tenant.                               |

- **Idempotencia:** deduplicar por `eventId`. El caso de uso ya es idempotente
  (una segunda aprobación no crea un segundo tenant), pero un consumidor que
  provisione recursos externos debe serlo también.

### `tenant.registration-rejected.v1`

- **Módulo productor:** `tenant` (`tenant.application.integration.TenantIntegrationEventMapper`).
- **Disparador de negocio:** un `PLATFORM_ADMIN` rechaza la solicitud con motivo
  obligatorio (`POST /api/v1/platform/registrations/{id}/reject`).
- **`aggregateId`:** id de la solicitud.

```json
{
  "eventId": "4b82ce60-7f35-4ba1-cd62-5e0f3a417283",
  "eventType": "tenant.registration-rejected.v1",
  "eventVersion": 1,
  "occurredAt": "2026-08-01T11:10:00Z",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "aggregateId": "7c1e2f30-4a5b-46c7-8d9e-0f1a2b3c4d5e",
  "payload": {
    "registrationId": "7c1e2f30-4a5b-46c7-8d9e-0f1a2b3c4d5e",
    "reason": "Dominio desechable"
  }
}
```

| `payload`        | Tipo   | Descripción                          |
| ---------------- | ------ | ------------------------------------ |
| `registrationId` | UUID   | Igual que `aggregateId`.             |
| `reason`         | string | Motivo del rechazo, siempre presente. |

- **Idempotencia:** deduplicar por `eventId`.
| `payload`      | Tipo   | Descripción                                                                 |
| -------------- | ------ | --------------------------------------------------------------------------- |
| `assignmentId` | UUID   | Igual que `aggregateId`.                                                    |
| `calendarId`   | UUID   | Calendario asignado.                                                        |
| `scope`        | string | `TENANT`, `TEAM` o `EMPLOYEE`, de menor a mayor precedencia.                 |
| `targetId`     | UUID   | Equipo o empleado destinatario. **Ausente** en ámbito `TENANT`.              |

- **`targetId` de ámbito `TEAM` es opaco:** el sistema no gestiona equipos
  (ADR-0017); no hay clave ajena ni garantía de que el equipo exista en ningún
  otro módulo.
- **Relevante para turnos y ausencias:** ambos eventos cambian qué calendario
  rige para los empleados afectados. Un consumidor que cachee la resolución debe
  invalidarla al recibirlos.
- **Idempotencia:** deduplicar por `eventId`. El estado autoritativo es siempre
  el de `GET /api/v1/admin/calendar-assignments/effective`; el evento es una
  notificación, no la fuente de verdad.

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
