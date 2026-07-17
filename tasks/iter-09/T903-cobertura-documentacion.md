# T903 — Cobertura final y documentación completa

- Iteración: 9 · Depende de: T902 · Contexto: CONTEXT-GLOBAL §8-9, SDD §20 y §24

## Objetivo
Cerrar los criterios de aceptación globales de documentación y cobertura.

## Detalle
1. Ejecutar JaCoCo y verificar dominio ≥90 %, aplicación ≥80 %; añadir tests donde falte (no excluir clases para maquillar). Publicar informe en `docs/testing/coverage-report.md` (tabla por módulo/capa + enlace al HTML de CI).
2. Frontend: revisar cobertura de `ng test --code-coverage` y completar tests de guards/interceptores/servicios core.
3. Repasar la sección 24 del SDD criterio a criterio y dejar `docs/acceptance-checklist.md` con evidencia por criterio (test o documento que lo cubre). Lo que no se cumpla → arreglarlo o listarlo como pendiente con motivo.
4. Documentación final: revisar y completar `docs/architecture/` (visión, contexto, contenedores, componentes actualizados al código real), `docs/domain/` (glosario, agregados, reglas), OpenAPI exportado a `docs/api/openapi.yaml` (generado del código), catálogo de eventos, estrategia de testing, política de IA. ADRs al día.

## Criterios de aceptación
- `mvn verify` y `ng test` verdes con umbrales; checklist de aceptación completo; documentación coherente con el código (verificación por muestreo).

## Ficheros previstos
Tests adicionales, `docs/**` actualizado, `docs/acceptance-checklist.md`.
