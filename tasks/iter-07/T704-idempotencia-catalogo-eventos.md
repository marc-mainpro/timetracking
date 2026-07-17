# T704 — Idempotencia, pruebas transaccionales finales y catálogo de eventos

- Iteración: 7 · Depende de: T703 · Contexto: CONTEXT-DOMINIO §4, SDD §13-14

## Objetivo
Cerrar la iteración de integración: garantías verificadas de extremo a extremo y contratos documentados.

## Detalle
1. Consumidor de ejemplo idempotente (interno, para demostrar el patrón): tabla `processed_event(event_id PK, processed_at)` + listener que ignora `eventId` ya procesado. Documentar el patrón para futuros consumidores.
2. Suite `OutboxGuaranteesIT` de extremo a extremo: acción de negocio real (cerrar jornada) → outbox → publicador → consumidor; re-entrega deliberada del mismo mensaje no produce efectos duplicados; atomicidad negocio+outbox re-verificada a nivel de flujo completo.
3. `docs/integration/event-catalog.md`: para CADA evento de integración — nombre, versión, descripción, disparador de negocio, esquema JSON del envelope y payload con ejemplo, semántica de entrega (at-least-once), notas de idempotencia. Añadir política de versionado (cambios incompatibles → `.v2`, nunca mutar `.v1`).
4. ADR si se tomó alguna decisión no fijada (p. ej. timeout de reclamación).

## Criterios de aceptación
- `mvn verify` verde; catálogo completo con los 8 eventos; el criterio global "los reintentos no producen efectos duplicados" queda cubierto por test.

## Ficheros previstos
`V7__processed_event.sql`, consumidor demo + tests, `docs/integration/event-catalog.md`.
