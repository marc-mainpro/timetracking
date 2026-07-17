# T603 — Auditoría append-only

- Iteración: 6 · Depende de: T602 (o en paralelo si define el puerto) · Contexto: CONTEXT-GLOBAL, SDD §15

## Objetivo
Registro de auditoría inmutable para acciones críticas.

## Detalle
1. `V5__audit.sql`: tabla `audit_event` (`id UUID PK`, `tenant_id`, `actor_user_id`, `action VARCHAR(100)`, `entity_type VARCHAR(100)`, `entity_id UUID`, `correlation_id UUID`, `metadata JSONB`, `occurred_at TIMESTAMPTZ NOT NULL`). Sin UPDATE/DELETE desde la app (opcional: `REVOKE`/trigger que lo impida, documentar).
2. `audit.application`: puerto `AuditRecorder.record(action, entityType, entityId, metadata)` — toma actor, tenant y correlationId del contexto. Adaptador JPA append-only (sin métodos de update/delete).
3. Integrar en acciones críticas existentes: registro de tenant, login fallido/éxito (sin credenciales), creación/activación/desactivación de empleado, asignación de roles, aprobación y rechazo de correcciones (obligatorio en aprobación). La escritura va en la misma transacción que la acción.
4. Endpoint de consulta para admin (decisión fijada: `GET /api/v1/admin/audit-events`, paginado, filtros `?action=&from=&to=`; solo lectura). No existe API de escritura/edición.
5. No almacenar secretos ni tokens en `metadata`.

## Pruebas
- Integración: cada acción crítica deja registro con actor/tenant/correlationId correctos; aprobación de corrección SIEMPRE audita; la API no permite mutar auditoría; cross-tenant: admin A no ve auditoría de B.

## Criterios de aceptación
- `mvn verify` verde; OpenAPI actualizado; `docs/security/` actualizado con la lista de acciones auditadas.

## Ficheros previstos
`V5__audit.sql`, `audit/**`, integraciones puntuales en casos de uso existentes, tests.
