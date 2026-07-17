## T604 — Concurrencia y bloqueo optimista

### Cambios

- Se añadió `version` real al agregado `CorrectionRequest` en dominio,
  persistencia JPA y migración `V7__correction_request_version.sql`.
- `GlobalExceptionHandler` ahora traduce también:
  - `OptimisticLockException`
  - `DataIntegrityViolationException` sobre `ux_workday_active`
- Se añadió un IT dedicado `concurrency/ConcurrencyIntegrationTest` con
  barreras sobre puertos de repositorio para forzar carreras reales sin usar
  `sleep`.
- Se documentó la estrategia de concurrencia en `docs/architecture/components.md`.

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B -Dtest=ConcurrencyIntegrationTest,CorrectionRequestTest,ApproveCorrectionRequestUseCaseTest,RejectCorrectionRequestUseCaseTest,GetCorrectionRequestUseCaseTest test
```

Resultado parcial: **BUILD SUCCESS**. `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`.

```text
cd backend && mvn -B verify
```

Resultado final: **BUILD SUCCESS**. `Tests run: 179, Failures: 0, Errors: 0, Skipped: 0`.

### Cobertura nueva específica

- carrera de doble cierre de jornada: exactamente una request gana y la otra
  recibe `409`
- carrera de doble aprobación de corrección: un único ajuste persistido y un
  único registro `CORRECTION_APPROVED` en auditoría
- carrera de doble apertura de jornada: una única jornada activa gracias a
  bloqueo lógico + índice único parcial
- carrera entre inicio de pausa y cierre de jornada: estado final consistente

### Seguridad

- Las carreras multitenant siguen scopeadas por `tenantId` resuelto desde el
  principal autenticado.
- La traducción de conflictos concurrentes evita errores `500` por violaciones
  de unicidad esperables bajo carga.

### Documentación actualizada

- `docs/architecture/components.md`

### ADR

- No fue necesario crear ADR nueva.

### Riesgos detectados

1. `DomainEventPublisher` provisional sigue publicando antes del commit real,
   así que en una colisión optimista puede verse un log de evento de dominio en
   una transacción que finalmente hace rollback. El estado persistido y la
   auditoría sí quedan consistentes; el desacoplo definitivo llegará con outbox.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T604.
- La siguiente tarea correcta es `T605`.
