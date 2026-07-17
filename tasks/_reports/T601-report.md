## T601 — Dominio CorrectionRequest + migración

### Cambios

- Se implementó `CorrectionRequest` en `corrections.domain` con estados:
  - `PENDING`
  - `APPROVED`
  - `REJECTED`
- Se implementó `ProposedChanges` como VO inmutable con validación temporal y pausas dentro de la jornada.
- Se añadieron las excepciones de negocio:
  - `CORRECTION_ALREADY_PENDING`
  - `CORRECTION_ALREADY_RESOLVED`
- Se añadieron eventos de dominio:
  - `CorrectionRequested`
  - `CorrectionApproved`
  - `CorrectionRejected`
- Se reforzó `WorkdayAdjustment` para validar que las pausas ajustadas queden dentro de la jornada.
- Se añadió `CorrectionRequestRepository` tenant-aware y su adaptador JPA con serialización manual de `proposedChanges` a JSONB.
- Se añadió la migración `V5__corrections.sql` con índice único parcial para una sola corrección `PENDING` por `(workday_id, requested_by)`.

### Pruebas (comandos ejecutados y resultado)

```text
cd backend && mvn -B verify
```

Resultado: **BUILD SUCCESS**. `Tests run: 164, Failures: 0, Errors: 0, Skipped: 0`.

Cobertura nueva específica:

- factoría, aprobación, rechazo y re-resolución de `CorrectionRequest`
- `ProposedChanges` incoherentes
- ajuste de `Workday` con pausas fuera de rango -> rechazo
- round-trip JSONB de `proposedChanges`
- índice único parcial de corrección pendiente

### Cobertura

- JaCoCo sigue en verde para los gates del proyecto.
- El nuevo dominio y persistencia de correcciones quedan cubiertos por unitarias e integración real.

### Seguridad

- `CorrectionRequestRepository` es tenant-aware por construcción.
- Las correcciones pendientes duplicadas quedan bloqueadas también a nivel de base de datos.
- No se introdujeron dependencias de Spring/JPA en el dominio.

### Documentación actualizada

- `tasks/STATUS.md`

### ADR

- No fue necesario crear ADR nueva.

### Riesgos detectados

1. Para evitar ciclos entre módulos, `Workday` sigue ajustándose a través de `WorkdayAdjustment`; `ProposedChanges` se convierte a ese VO en vez de acoplar directamente `timetracking` a `corrections`.
2. La numeración de migraciones de la ficha original (`V4__corrections.sql`) quedó obsoleta porque `V4` ya se usó en `timetracking`; se implementó correctamente como `V5__corrections.sql`.

### Pendientes / decisiones que necesitan humano

- Ninguna bloqueante para cerrar T601.
- La siguiente tarea correcta es `T602`.
