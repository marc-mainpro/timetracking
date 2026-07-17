## T202 — Dominio Tenant y User + persistencia

### Cambios

**`tenant` — domain (dominio puro, sin Spring/JPA)**
- `tenant/domain/TenantStatus.java` (nuevo): enum `ACTIVE|INACTIVE`.
- `tenant/domain/Tenant.java` (nuevo): agregado raíz. Factoría `register(name, timezone, Clock, IdGenerator)` valida nombre obligatorio (no vacío, trim) y timezone IANA vía `ZoneId.of(...)` (lanza `IllegalArgumentException` si falla); genera el evento `TenantRegistered`. `reconstitute(...)` para rehidratar desde persistencia sin generar eventos. `deactivate(Clock)` e `isActive()`. `pullDomainEvents()` devuelve y limpia la lista interna de eventos.
- `tenant/domain/TenantRepository.java` (nuevo): puerto — `save`, `findById`, `existsById`.
- `tenant/domain/event/TenantRegistered.java` (nuevo): record inmutable (`eventId`, `occurredAt`, `tenantId`, `aggregateId`, `name`, `timezone`).

**`identity` — domain (dominio puro, sin Spring/JPA)**
- `identity/domain/Email.java` (nuevo): VO — `of(raw)` normaliza a minúsculas, valida formato básico, `equals`/`hashCode` por valor normalizado.
- `identity/domain/Role.java` (nuevo): enum `TENANT_ADMIN|EMPLOYEE`.
- `identity/domain/UserStatus.java` (nuevo): enum `ACTIVE|INACTIVE`.
- `identity/domain/User.java` (nuevo): agregado raíz. Factoría `create(tenantId, rawEmail, passwordHash, firstName, lastName, roles, Clock, IdGenerator)` valida email (vía `Email.of`), nombre/apellido no vacíos y roles no vacíos; genera `EmployeeCreated`. `reconstitute(...)` sin eventos. `activate(Clock)`, `deactivate(Clock, IdGenerator)` (genera `EmployeeDeactivated`), `assignRoles(Set<Role>, Clock)`, `isActive()`, `pullDomainEvents()`.
- `identity/domain/UserRepository.java` (nuevo): puerto — `save`, `findById`, `findByTenantIdAndEmail`, `existsByTenantIdAndEmail` (consultas acotadas por `tenantId`, CONTEXT-GLOBAL §5).
- `identity/domain/event/EmployeeCreated.java`, `identity/domain/event/EmployeeDeactivated.java` (nuevos): records inmutables.

**`tenant`/`identity` — infrastructure.persistence (adaptadores JPA)**
- `tenant/infrastructure/persistence/TenantJpaEntity.java`, `TenantMapper.java` (package-private), `TenantJpaRepository.java` (Spring Data, package-private), `TenantRepositoryAdapter.java` (`@Repository`, implementa `TenantRepository`).
- `identity/infrastructure/persistence/UserJpaEntity.java` (roles como `@ElementCollection` sobre `user_role`, `@CollectionTable(joinColumns = user_id)`, columna `role`), `UserMapper.java`, `UserJpaRepository.java` (`findByTenantIdAndEmail`, `existsByTenantIdAndEmail`), `UserRepositoryAdapter.java` (implementa `UserRepository`).

Ninguna migración SQL se ha tocado (T201 ya creó `tenant`, `app_user`, `user_role`, `refresh_token`); las entidades JPA se mapean 1:1 contra ese esquema (`ddl-auto: validate` lo confirma en cada arranque de test).

### Pruebas (comandos ejecutados y resultado)
- `mvn -B compile` / `mvn -B test-compile` → OK.
- `mvn -B verify` (desde `backend/`) → **BUILD SUCCESS**.
  - `Tests run: 53, Failures: 0, Errors: 0, Skipped: 0`.
  - Nuevas suites: `TenantTest` (10), `EmailTest` (7), `UserTest` (13) — dominio puro, sin Spring; `TenantRepositoryAdapterIntegrationTest` (4) y `UserRepositoryAdapterIntegrationTest` (5) — Testcontainers PostgreSQL, contexto Spring Boot completo (`@SpringBootTest(webEnvironment = RANDOM_PORT)`).
  - ArchUnit (`LayeredArchitectureTest`, `DomainPurityTest`, `ModuleCyclesTest`, `OutboxEncapsulationTest`, `DomainEventImmutabilityTest`, `RestLayerAccessTest`) → verdes.
  - `jacoco-maven-plugin:check` (dominio ≥90 %, aplicación ≥80 %) → `All coverage checks have been met.`

### Cobertura
LINE coverage (jacoco.xml) de los paquetes tocados:
- `tenant/domain`: 49/49 = **100.0 %**
- `tenant/domain/event`: 1/1 = **100.0 %**
- `tenant/infrastructure/persistence`: 37/37 = **100.0 %**
- `identity/domain`: 113/115 = **98.3 %**
- `identity/domain/event`: 4/4 = **100.0 %**
- `identity/infrastructure/persistence`: 57/57 = **100.0 %**

Todos por encima del umbral de dominio (≥90 %) exigido por CONTEXT-GLOBAL §8 / la ficha.

### Seguridad
- El agregado `User` recibe `passwordHash` ya calculado (fuera de alcance el hashing, según ficha); no se loguea en ningún test.
- `Email.of` normaliza a minúsculas antes de comparar/persistir, evitando duplicados por capitalización distinta dentro del mismo tenant.
- El test de integración `UserRepositoryAdapterIntegrationTest.rejectsSameEmailWithinSameTenant` confirma que la UNIQUE `(tenant_id, email)` de V2__identity.sql sigue protegiendo la multitenancy a nivel de adaptador JPA (no solo SQL crudo).
- Ninguna consulta del puerto `UserRepository` permite acceso cross-tenant: todas están acotadas por `tenantId` explícito.

### Documentación actualizada
Ninguna. Revisé `docs/domain/agregados.md` y `docs/domain/reglas-de-negocio.md`: ya documentan los campos y reglas de Tenant/User (nombre obligatorio, timezone IANA, email único por tenant, usuario inactivo no autentica, un usuario por tenant) copiados de CONTEXT-DOMINIO §1-2, y ninguno de los dos ficheros lista eventos de dominio para ningún agregado (tampoco lo hace para Workday/CorrectionRequest), así que no he añadido una sección de eventos para no romper la convención existente del documento; los eventos `TenantRegistered`/`EmployeeCreated`/`EmployeeDeactivated` ya están catalogados en CONTEXT-DOMINIO §3, que es la fuente de verdad citada por ambos documentos.

### ADR
Ninguna decisión nueva fuera de lo ya fijado en CONTEXT-GLOBAL/CONTEXT-DOMINIO: factorías validan invariantes ya descritas en la ficha; el resto (records para eventos, adaptadores Spring Data, `@ElementCollection` para roles) es aplicación directa de las skills `create-domain-event`/`create-use-case` y de las restricciones ArchUnit ya activas.

### Riesgos detectados
- **Los tests de integración (`*IT.java`) nunca se ejecutaban.** El `pom.xml` no tiene el plugin `failsafe` enlazado y Surefire usa sus patrones por defecto (`**/*Test.java`, no `**/*IT.java`), por lo que `FlywayIdentityMigrationIT` (de T201) jamás corría bajo `mvn verify`, pese a que el informe de T201 indicaba "14 tests" incluyéndolo. Para que mis nuevos tests de integración (Tenant/User ↔ JPA) se ejecutasen de verdad, los nombré `*IntegrationTest.java` (patrón que Surefire sí recoge) en lugar de `*IT.java`. **No he tocado `FlywayIdentityMigrationIT` ni el `pom.xml`** por estar fuera del alcance declarado de esta ficha (`tenant`, `identity`, `shared`); recomiendo a un humano decidir si se añade `maven-failsafe-plugin` enlazado a `integration-test`/`verify`, o si se renombra `FlywayIdentityMigrationIT` a `*IntegrationTest` para que Surefire lo ejecute — mientras tanto ese test concreto **no se está verificando en ningún `mvn verify`**.
- Relacionado con lo anterior: al intentar ejecutar mis tests con `@SpringBootTest(webEnvironment = NONE)` (patrón que ya usaba `FlywayIdentityMigrationIT`), el arranque falla porque `SecurityConfig` (T101) exige un bean `HttpSecurity`, que solo está disponible con un contexto servlet. Cambié mis dos tests a `webEnvironment = RANDOM_PORT` (mismo patrón que `ApplicationSmokeTest`), lo cual funciona pero es más pesado (levanta Tomcat embebido). Este mismo problema afectaría a `FlywayIdentityMigrationIT` si algún día se activa su ejecución.
- `Tenant.deactivate()` no genera evento de dominio (el catálogo de CONTEXT-DOMINIO §3 no incluye `TenantDeactivated`); si una iteración futura lo necesita, habrá que añadirlo explícitamente.
- La factoría `User.create(...)` valida `firstName`/`lastName` no vacíos aunque la ficha solo detalla explícitamente "email válido... roles no vacíos"; lo añadí por consistencia con los campos `NOT NULL` de `app_user` (V2__identity.sql). Si se considera fuera de alcance, es trivial revertir esa validación puntual.

### Pendientes / decisiones que necesitan humano
1. Decidir cómo resolver el hallazgo de `*IT.java` no ejecutándose (añadir `failsafe`, o renombrar a `*IntegrationTest`) — afecta a `FlywayIdentityMigrationIT` de T201, fuera del alcance de esta ficha.
2. Cuándo crear `docs/domain/agregados.md` / `docs/domain/reglas-de-negocio.md` (aún no existen en el repo); probablemente corresponda a T203/T204 al añadir los casos de uso que consumen estas reglas.
