# T105 — AGENTS.md, estructura de documentación, ADR iniciales y skills

- Iteración: 1 · Depende de: — (paralelo a T101) · Contexto: CONTEXT-GLOBAL, CONTEXT-DOMINIO

## Objetivo
Crear la documentación viva base, las decisiones iniciales y las guías operativas para agentes.

## Detalle
1. Copiar el `AGENTS.md` literal de la sección 21 del SDD a la raíz.
2. Crear `docs/` con: `architecture/` (visión, contexto C4, contenedores, componentes — versiones iniciales de 1 página), `adr/`, `domain/` (glosario + agregados + reglas de negocio, tomados de CONTEXT-DOMINIO), `api/`, `integration/` (placeholder `event-catalog.md`), `security/` (modelo de amenazas inicial: STRIDE resumido sobre auth, multitenancy, outbox), `testing/` (estrategia según CONTEXT-GLOBAL §8), `ai/` (política de uso de IA: revisión humana obligatoria, no commitear sin tests).
3. ADR iniciales (formato MADR corto, estado "accepted"): ADR-0001 monolito modular; ADR-0002 multitenancy por columna tenant_id con esquema compartido; ADR-0003 Maven + paquete base + estructura de módulos; ADR-0004 JWT + refresh rotatorio en cookie HttpOnly; ADR-0005 Transactional Outbox con polling sin broker; ADR-0006 Problem Details para errores.
4. Crear las 8 skills de la sección 22 del SDD en `.skills/<nombre>/SKILL.md`, cada una con: Objetivo, Entradas, Pasos, Validaciones, Pruebas, Criterios de finalización, Archivos que puede modificar, Archivos que debe actualizar. Basarlas en las reglas de los CONTEXT-*.

## Fuera de alcance
OpenAPI generado (sale del código en tareas posteriores), catálogo de eventos completo (T704).

## Criterios de aceptación
- Estructura `docs/` completa según sección 20 del SDD; ADR 1-6 escritos; 8 skills creadas con las 8 secciones requeridas.

## Ficheros previstos
`AGENTS.md`, `docs/**`, `.skills/**`.
