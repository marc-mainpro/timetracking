## T201 — Migración de base de datos: tenant, user, refresh_token

### Cambios
- `backend/src/main/resources/db/migration/V2__identity.sql` (nuevo): crea las tablas de identidad requeridas por la ficha:
  - `tenant`: `id UUID PK`, `name VARCHAR(200) NOT NULL`, `status VARCHAR(20) NOT NULL`, `timezone VARCHAR(60) NOT NULL`, `created_at`/`updated_at TIMESTAMPTZ NOT NULL`.
  - `app_user`: `id UUID PK`, `tenant_id UUID NOT NULL` FK → `tenant(id)`, `email VARCHAR(320) NOT NULL`, `password_hash VARCHAR(100) NOT NULL`, `first_name`, `last_name`, `status VARCHAR(20) NOT NULL`, `created_at`/`updated_at`; `UNIQUE (tenant_id, email)` (`uq_app_user_tenant_email`) e índice `ix_app_user_tenant_id` sobre `tenant_id` (multitenancy, CONTEXT-GLOBAL §5).
  - `user_role`: tabla separada `(user_id, role)` con **PK compuesta** `(user_id, role)` y FK a `app_user(id)` — decisión ya fijada en la ficha (elegida frente a CSV en columna).
  - `refresh_token`: `id UUID PK`, `user_id UUID NOT NULL` FK → `app_user(id)`, `token_hash VARCHAR(64) NOT NULL UNIQUE`, `expires_at TIMESTAMPTZ NOT NULL`, `revoked_at TIMESTAMPTZ` (nullable), `replaced_by UUID` (nullable), `created_at TIMESTAMPTZ NOT NULL`; índice `ix_refresh_token_user_id` sobre `user_id`.
  - Todas las columnas temporales son `TIMESTAMPTZ` (persistencia UTC → `Instant`, según skill y CONTEXT-GLOBAL §3). No se ha tocado `V1__baseline.sql` (migración ya aplicada, no se reedita).
- `backend/src/test/java/com/tfp/timetracking/FlywayIdentityMigrationIT.java` (nuevo): test de integración con Testcontainers (`postgres:16-alpine`) que:
  1. Arranca el contexto de Spring desde base de datos vacía y verifica que Flyway aplica `V1` + `V2`, comprobando la existencia de las 4 tablas (`tenant`, `app_user`, `user_role`, `refresh_token`) vía metadatos JDBC.
  2. Inserta el mismo email en dos tenants distintos y comprueba que **no** lanza excepción (multitenancy: unicidad de email es relativa al tenant).
  3. Inserta el mismo email dos veces en el mismo tenant y comprueba que lanza `DataIntegrityViolationException` (violación de `UNIQUE (tenant_id, email)`).

### Pruebas (comandos ejecutados y resultado)
- `mvn -B verify` (desde `backend/`) → **BUILD SUCCESS**.
  - `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` (11 tests preexistentes + 3 nuevos de `FlywayIdentityMigrationIT`).
  - Logs de Flyway confirman: `Migrating schema "public" to version "2 - identity"` y `Successfully applied 2 migrations to schema "public", now at version v2` desde esquema vacío (nuevo contenedor Testcontainers por test, sin estado previo).
  - `jacoco-maven-plugin:check` (dominio y aplicación) → `All coverage checks have been met.`
  - ArchUnit (`LayeredArchitectureTest`, `DomainPurityTest`, `ModuleCyclesTest`, `OutboxEncapsulationTest`, `DomainEventImmutabilityTest`, `RestLayerAccessTest`) → verdes, sin cambios de código de producción fuera de SQL.

### Cobertura
Sin cambio de comportamiento en `main` (solo SQL de migración); no se han añadido clases Java de producción, por lo que los umbrales de JaCoCo (dominio ≥90 %, aplicación ≥80 %) no se ven afectados y siguen en verde según el check de Maven.

### Seguridad
- `password_hash` y `token_hash` se persisten como columnas opacas; el test no registra ni loguea valores sensibles (usa literales de prueba `"hash"`).
- `UNIQUE (tenant_id, email)` impide fugas de unicidad entre tenants (regla CONTEXT-GLOBAL §5); `refresh_token.token_hash` es `UNIQUE` global (hash SHA-256 de 64 hex chars, ya fijado en CONTEXT-GLOBAL §3).
- Nada de datos ni secretos reales en la migración; solo DDL.

### Documentación actualizada
Ninguna en `docs/`: la ficha limita el alcance a la migración SQL y su test; los campos coinciden exactamente con lo ya documentado en `CONTEXT-DOMINIO.md` §1 (Tenant, User), por lo que no procede actualizar `docs/domain/agregados.md` en esta tarea (las entidades JPA y su mapeo, incluido cualquier ajuste de documentación de agregados si aplica, quedan para T202+, según "Fuera de alcance" de la ficha).

### ADR
Ninguna decisión nueva: la elección de tabla `user_role(user_id, role)` con PK compuesta ya venía fijada explícitamente en la ficha T201, y el resto de tipos/constraints son aplicación directa de CONTEXT-GLOBAL (UUID, TIMESTAMPTZ, `UNIQUE (tenant_id, email)`).

### Riesgos detectados
- `refresh_token.replaced_by` no lleva FK a `refresh_token.id` (no se especificaba en la ficha); si en T2xx se implementa la rotación con detección de reutilización, podría convenir añadir la FK en una migración posterior (no destructiva).
- Ninguna entidad JPA mapea aún estas tablas (`ddl-auto: validate` no las valida todavía); la coherencia entre este esquema y las entidades de T202 deberá comprobarse en esa tarea.

### Pendientes / decisiones que necesitan humano
Ninguno. Tarea completada dentro del alcance exacto de la ficha (solo migración `V2__identity.sql` y su test de integración), sin tocar entidades JPA ni lógica de aplicación (reservado a T202+).
