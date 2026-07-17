# Skill: create-database-migration

## Objetivo

Añadir o modificar el esquema de PostgreSQL de forma reproducible con
Flyway, manteniendo la multitenancy y las restricciones de integridad del
dominio.

## Entradas

- Cambio de modelo necesario (nueva tabla, columna, índice, constraint).
- Agregado(s) de dominio afectados (ver `docs/domain/agregados.md`).

## Pasos

1. Determinar el siguiente número de versión libre en
   `backend/src/main/resources/db/migration`.
2. Crear el fichero `V<N>__descripcion.sql` (nombre descriptivo en
   minúsculas, sin espacios).
3. Si la tabla es de negocio, incluir SIEMPRE la columna `tenant_id`
   (referencia a `tenant.id`), NOT NULL.
4. Definir claves primarias como UUID (generadas en la aplicación, no
   `SERIAL`/`IDENTITY`).
5. Añadir las restricciones `UNIQUE` compuestas con `tenant_id` cuando la
   unicidad es relativa al tenant (p. ej. `UNIQUE (tenant_id, email)`).
6. Añadir índices para las columnas usadas en filtros frecuentes
   (`tenant_id`, claves foráneas, `status`).
7. Nunca modificar una migración ya aplicada/mergeada: crear una nueva
   migración correctiva.
8. Verificar que las columnas temporales usan `TIMESTAMPTZ` (persistencia en
   UTC, mapeadas a `Instant` en el dominio).

## Validaciones

- Toda tabla de negocio tiene `tenant_id`.
- Toda migración es idempotente en el sentido de Flyway (no se reedita tras
  aplicarse).
- No hay `DROP`/`TRUNCATE` destructivos sin justificación y ADR si afecta a
  datos de producción.
- Nombres de columnas y tablas en snake_case, consistentes con el resto del
  esquema.

## Pruebas

- Test de integración con Testcontainers que arranca PostgreSQL, aplica
  todas las migraciones (`flyway migrate` vía Spring Boot) y verifica que el
  esquema resultante es el esperado.
- Si la migración soporta un agregado nuevo, añadir también los tests de
  repositorio correspondientes.

## Criterios de finalización

- Las migraciones se aplican limpiamente desde cero (`mvn verify` con
  Testcontainers) y también sobre una base ya migrada con versiones
  anteriores.
- `mvn verify` en verde.

## Archivos que puede modificar

- `backend/src/main/resources/db/migration/V<N>__*.sql` (solo ficheros
  nuevos).
- `backend/src/test/java/**` (tests de migración/repositorio).

## Archivos que debe actualizar

- `docs/domain/agregados.md` si la migración introduce/cambia campos de un
  agregado documentado.
- `docs/adr/` con un nuevo ADR si el cambio de esquema implica una decisión
  no fijada en `tasks/_context/CONTEXT-GLOBAL.md`.
