# T604 — Concurrencia y bloqueo optimista

- Iteración: 6 · Depende de: T602 · Contexto: CONTEXT-GLOBAL §7, SDD §17

## Objetivo
Verificar y endurecer el comportamiento del sistema ante operaciones simultáneas.

## Detalle
1. Revisar que `Workday` y `CorrectionRequest` usan `@Version` y que todo `OptimisticLockException`/`ObjectOptimisticLockingFailureException` se mapea a 409 `CONCURRENT_MODIFICATION` en el handler global.
2. Tests de integración de carreras (dos hilos con barrera / `CompletableFuture` sobre la API o casos de uso):
   - Doble cierre simultáneo de jornada → exactamente uno gana, el otro 409.
   - Doble aprobación simultánea de la misma corrección → una gana, otra 409; la jornada se ajusta UNA vez y hay UN registro de auditoría de aprobación.
   - Doble `start` de jornada simultáneo → una jornada creada (índice único parcial de T402 como red).
   - Inicio de pausa simultáneo al cierre → estado final consistente.
3. Documentar la estrategia de concurrencia en `docs/architecture/`.

## Criterios de aceptación
- `mvn verify` verde con los tests de carrera pasando de forma estable (sin flakiness: usar latches, no sleeps).

## Ficheros previstos
`ConcurrencyIT.java` (o por módulo), ajustes en handler/entidades si faltan, docs.
