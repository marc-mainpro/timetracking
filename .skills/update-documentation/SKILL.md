# Skill: update-documentation

## Objetivo

Mantener sincronizada la documentación viva en `docs/` con el estado real
del código al cierre de cada tarea, según la estructura fijada en la sección
20 del SDD.

## Entradas

- Diff de la tarea en curso (código, migraciones, tests).
- Ficha de la tarea (`tasks/iter-XX/TXXX-*.md`) y su informe
  (`tasks/_reports/TXXX-report.md`).

## Pasos

1. Identificar qué documentos de `docs/` describen algo que el cambio
   afecta: arquitectura, dominio, API, eventos, seguridad, testing, ADR.
2. Si cambió un endpoint o su contrato: regenerar/actualizar
   `docs/api/README.md` (o el export OpenAPI) para que coincida con el
   código.
3. Si cambió una regla de negocio, un agregado o un `errorCode`: actualizar
   `docs/domain/agregados.md` y/o `docs/domain/reglas-de-negocio.md`.
4. Si cambió un evento de dominio o de integración: actualizar
   `docs/integration/event-catalog.md`.
5. Si se tomó una decisión de arquitectura no fijada en
   `tasks/_context/CONTEXT-GLOBAL.md`: crear un ADR nuevo en `docs/adr/`
   (nunca reescribir uno ya aceptado).
6. Tras `mvn verify`, actualizar `docs/testing/informe-cobertura.md` con las
   cifras vigentes de JaCoCo.
7. Si el cambio afecta a autenticación, multitenancy u outbox: revisar y, si
   procede, actualizar `docs/security/threat-model.md`.
8. Redactar el informe de la tarea en `tasks/_reports/TXXX-report.md` con el
   formato de `tasks/_context/CONTEXT-GLOBAL.md` §10.

## Validaciones

- Ningún documento describe un endpoint, regla o evento que ya no existe o
  que cambió de comportamiento.
- Todo ADR nuevo usa el formato MADR corto y estado `accepted` (o
  `proposed`/`superseded` si aplica) y no reescribe uno existente.
- El informe de tarea contiene las 7 secciones obligatorias.

## Pruebas

- No aplica pruebas automatizadas de documentación en el MVP; se verifica
  por revisión humana (ver `docs/ai/politica-uso-ia.md`) que el contenido es
  coherente con el código y los CONTEXT-*.

## Criterios de finalización

- `docs/` refleja el estado real del código tras la tarea.
- `tasks/_reports/TXXX-report.md` existe y está completo.
- Ningún ADR aceptado fue modificado retroactivamente sin registrar una
  decisión nueva.

## Archivos que puede modificar

- `docs/**`
- `tasks/_reports/TXXX-report.md`

## Archivos que debe actualizar

- `docs/adr/` (nuevo ADR) cuando la tarea tomó una decisión no fijada.
- `docs/testing/informe-cobertura.md` tras cada `mvn verify`.
- `docs/integration/event-catalog.md` cuando cambian contratos de eventos.
