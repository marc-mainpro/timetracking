## T105 — AGENTS.md, estructura de documentación, ADR iniciales y skills

### Cambios
- Creado `AGENTS.md` en la raíz del repositorio, copia literal de la sección 21 del SDD.
- Creada la estructura `docs/` completa según la sección 20 del SDD:
  - `docs/architecture/`: `vision.md`, `context.md`, `containers.md`, `components.md`.
  - `docs/adr/`: `ADR-0001-monolito-modular.md`, `ADR-0002-multitenancy-columna-tenant-id.md`, `ADR-0003-maven-paquete-base-estructura-modulos.md`, `ADR-0004-jwt-refresh-rotatorio-cookie-httponly.md`, `ADR-0005-transactional-outbox-sin-broker.md`, `ADR-0006-problem-details-errores.md`.
  - `docs/domain/`: `glosario.md`, `agregados.md`, `reglas-de-negocio.md` (basados en CONTEXT-DOMINIO).
  - `docs/api/README.md`: placeholder con formato de error y roles (el OpenAPI generado llega en tareas posteriores).
  - `docs/integration/event-catalog.md`: placeholder con envelope y tipos previstos (catálogo completo en T704).
  - `docs/security/threat-model.md`: modelo de amenazas STRIDE resumido sobre autenticación, multitenancy y outbox.
  - `docs/testing/estrategia.md` e `informe-cobertura.md` (placeholder, sin código de dominio aún).
  - `docs/ai/politica-uso-ia.md`: política de uso de IA (revisión humana obligatoria, no commitear sin tests, etc.).
- Creadas las 8 skills en `.skills/<nombre>/SKILL.md` con las 8 secciones requeridas (Objetivo, Entradas, Pasos, Validaciones, Pruebas, Criterios de finalización, Archivos que puede modificar, Archivos que debe actualizar):
  `create-use-case`, `create-rest-endpoint`, `create-database-migration`, `review-multitenancy`, `create-domain-event`, `create-integration-event`, `review-outbox`, `update-documentation`.

### Pruebas (comandos ejecutados y resultado)
No aplica: tarea puramente documental, sin código ejecutable. No se han ejecutado `mvn verify` ni `ng test` (no procede en esta tarea).

### Cobertura
No aplica (sin código de dominio/aplicación todavía). `docs/testing/informe-cobertura.md` queda como placeholder a completar desde la iteración 2.

### Seguridad
Se documentó el modelo de amenazas STRIDE inicial (`docs/security/threat-model.md`) cubriendo autenticación JWT/refresh, multitenancy y Outbox, según lo fijado en CONTEXT-GLOBAL §3 y §6. No se ha escrito ni expuesto ningún secreto.

### Documentación actualizada
Toda la estructura `docs/` creada en esta tarea (ver "Cambios"). `AGENTS.md` en la raíz.

### ADR
ADR-0001 a ADR-0006 creados en formato MADR corto, estado `accepted`, reflejando exactamente las decisiones fijadas en CONTEXT-GLOBAL §3 (monolito modular, multitenancy por `tenant_id`, Maven + paquete `com.tfp.timetracking` + estructura de módulos/capas, JWT + refresh rotatorio en cookie HttpOnly, Transactional Outbox sin broker, Problem Details RFC 7807).

### Riesgos detectados
- Los documentos de dominio y de arquitectura describen el modelo objetivo; deberán revisarse y ajustarse ligeramente en cada iteración a medida que el código real (paquetes, entidades) se implemente, para no divergir.
- `docs/testing/informe-cobertura.md` y `docs/api/README.md` son placeholders intencionados (fuera de alcance de T105); quedan pendientes de contenido real en T201+ y en cada tarea de endpoints.

### Pendientes / decisiones que necesitan humano
Ninguna. La tarea no requirió decisiones fuera de lo ya fijado en CONTEXT-GLOBAL/CONTEXT-DOMINIO.
