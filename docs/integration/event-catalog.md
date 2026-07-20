# Catálogo de eventos de integración (placeholder)

Este catálogo se completará en la iteración 7 (T704): esquema detallado del
`payload` por tipo, productor, consumidores conocidos y ejemplo de mensaje.
T702 implementó la traducción `DomainEvent -> IntegrationEvent` y la
escritura atómica en `outbox_message`; esta sección se mantiene coherente con
esa implementación, sin adelantar el contenido completo de T704.

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

Nota de implementación (T702): internamente, `IntegrationEvent`
(`shared.domain.IntegrationEvent`) añade un campo `aggregateType` (p.ej.
`"Workday"`, `"Tenant"`, `"Employee"`, `"CorrectionRequest"`) que no forma
parte del envelope público de arriba; alimenta la columna
`aggregate_type` de `outbox_message`, usada por el futuro publicador (T703)
para logging/routing, no por los consumidores externos.

## Tipos previstos

- `tenant.registered.v1`
- `identity.employee-created.v1`
- `identity.employee-deactivated.v1`
- `time-tracking.workday-started.v1`
- `time-tracking.workday-closed.v1`
- `corrections.correction-requested.v1`
- `corrections.correction-approved.v1`
- `corrections.correction-rejected.v1`

Eventos de dominio que **no** se traducen a evento de integración en este
MVP (decisión T702): `BreakStarted`/`BreakEnded` (módulo `timetracking`).
Son de interés solo interno (consistencia del agregado `Workday`); ningún
tipo de esta lista los necesita. Ver
`timetracking.application.integration.TimeTrackingIntegrationEventMapper`.

## Reglas

- Los eventos de integración se escriben en `outbox_message` en la misma
  transacción que el cambio de negocio (Transactional Outbox, ADR-0005): un
  mapper por módulo (`*/application/integration/`) traduce el evento de
  dominio a `IntegrationEvent`, y `OutboxDomainEventPublisher`
  (`outbox.infrastructure`) lo persiste vía el puerto `OutboxWriter` dentro
  de la misma transacción `@Transactional` del caso de uso.
- Nunca se publican entidades JPA ni modelos internos.
- Entrega at-least-once; los consumidores deben ser idempotentes.
