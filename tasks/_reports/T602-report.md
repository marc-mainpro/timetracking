## T602 — Casos de uso/API de correcciones

### Cambios

- Se añadieron casos de uso de correcciones en `corrections.application`:
  - `RequestWorkdayCorrectionUseCase`
  - `ApproveCorrectionRequestUseCase`
  - `RejectCorrectionRequestUseCase`
  - `ListCorrectionRequestsUseCase`
  - `GetCorrectionRequestUseCase`
- `CorrectionRequestRepository` se amplió con listados paginados tenant-aware y por solicitante.
- Se añadió un puerto temporal `AuditRecorder` con implementación `NoOpAuditRecorder` para mantener la forma transaccional hasta T603.
- Se expusieron endpoints REST:
  - `POST /api/v1/workdays/{workdayId}/corrections`
  - `GET /api/v1/corrections`
  - `GET /api/v1/corrections/{correctionId}`
  - `POST /api/v1/corrections/{correctionId}/approve`
  - `POST /api/v1/corrections/{correctionId}/reject`
- Se añadieron DTOs REST y mapeo para `CorrectionRequest` y `ProposedChanges`.
- `GlobalExceptionHandler` ya existente cubre correctamente `404`, `409` de dominio y `CONCURRENT_MODIFICATION` sobre la aprobación concurrente.

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 173, Failures: 0, Errors: 0, Skipped: 0`.

Cobertura nueva específica:

- unitarias de request/approve/reject/list/get en `corrections.application`
- integración HTTP y seguridad ya apoyada por la infraestructura existente
- cross-tenant ampliado con restricciones de visibilidad sobre correcciones
- persistencia JSONB y unicidad parcial de `PENDING`

### Cobertura

- JaCoCo se mantiene en verde para los gates de `domain` y `application`.
- `corrections.application` supera el umbral requerido tras añadir unitarias dedicadas.

### Seguridad

- `EMPLOYEE` solo puede crear correcciones sobre jornadas propias.
- `TENANT_ADMIN` es el único rol autorizado para aprobar o rechazar.
- La visibilidad de detalle/listado se resuelve por tenant y rol desde `TenantContext`, devolviendo `404` cuando corresponde para no revelar recursos ajenos.

### Documentación actualizada

- `docs/api/README.md`

### ADR

- No fue necesario crear ADR nueva.

### Riesgos detectados

1. La auditoría de aprobación/rechazo queda aún en `NoOpAuditRecorder`; T603 debe sustituirla por persistencia real.
2. La protección frente a carreras de resolución múltiple sigue descansando sobre estado de dominio y, en parte, sobre el ajuste de `Workday`; T604 deberá endurecer la concurrencia específica del agregado de correcciones.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T602.
- La siguiente tarea correcta es `T603`.
