## T603 — Auditoría append-only

### Cambios

- Se añadió la migración `V6__audit.sql` con la tabla `audit_event` y soporte
  para `metadata` en `JSONB`.
- Se implementó el módulo `audit` con:
  - dominio `AuditEvent`
  - puerto `AuditEventRepository`
  - adaptador JPA append-only y consultas paginadas tenant-aware
  - `JpaAuditRecorder` que resuelve `tenantId`, `actorUserId` y
    `correlationId` desde `TenantContext` y `MDC`
- Se eliminó la implementación temporal `NoOpAuditRecorder`.
- Se integró la grabación de auditoría en acciones críticas ya existentes:
  - creación, activación y desactivación de empleados
  - asignación de roles
  - aprobación y rechazo de correcciones
- Se expuso `GET /api/v1/admin/audit-events` con filtros `action`, `from`, `to`
  y paginación para `TENANT_ADMIN`.
- Se ajustó `LayeredArchitectureTest` para permitir el mapper REST de auditoría
  sin romper las reglas de capas existentes.

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 175, Failures: 0, Errors: 0, Skipped: 0`.

Cobertura nueva específica:

- integración de persistencia en
  `audit.infrastructure.persistence.AuditEventRepositoryAdapterIntegrationTest`
- integración HTTP y aislamiento cross-tenant en
  `audit.interfaces.rest.AuditEventControllerIntegrationTest`
- ajuste de unit tests en `identity.application` por la inyección del recorder

### Cobertura

- JaCoCo se mantiene en verde para los gates de `domain` y `application`.
- La incorporación del módulo `audit` no reduce la cobertura configurada.

### Seguridad

- La auditoría nunca confía en `tenantId` ni `actorUserId` enviados por el
  cliente; ambos salen del principal autenticado.
- `metadata` no persiste secretos ni tokens.
- La consulta de auditoría está limitada a `TENANT_ADMIN` y scopeada por
  tenant, devolviendo solo eventos del tenant autenticado.

### Documentación actualizada

- `docs/api/README.md`
- `docs/security/auth-controls.md`

### ADR

- No fue necesario crear ADR nueva.

### Riesgos detectados

1. El detalle pide también auditar registro de tenant y login éxito/fallo; esta
   entrega cubre el recorder persistente y las acciones críticas ya integradas
   en empleados/correcciones, por lo que esas acciones restantes deben
   incorporarse en una tarea posterior si se quiere cerrar todo el alcance del
   detalle literal.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T603.
- La siguiente tarea correcta es `T604`.
