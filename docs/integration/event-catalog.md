# Catálogo de eventos de integración (placeholder)

Este catálogo se completará en la iteración 7 (T704). Contendrá, para cada
tipo de evento: nombre, versión, esquema del `payload`, productor,
consumidores conocidos y ejemplo de mensaje.

## Envelope

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

## Tipos previstos

- `tenant.registered.v1`
- `identity.employee-created.v1`
- `identity.employee-deactivated.v1`
- `time-tracking.workday-started.v1`
- `time-tracking.workday-closed.v1`
- `corrections.correction-requested.v1`
- `corrections.correction-approved.v1`
- `corrections.correction-rejected.v1`

## Reglas

- Los eventos de integración se escriben en `outbox_message` en la misma
  transacción que el cambio de negocio.
- Nunca se publican entidades JPA ni modelos internos.
- Entrega at-least-once; los consumidores deben ser idempotentes.
