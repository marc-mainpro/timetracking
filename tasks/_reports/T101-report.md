## T101 — Scaffolding del backend Spring Boot

### Cambios
- Creado `backend/pom.xml`: proyecto Maven único, `spring-boot-starter-parent` **3.5.9** (Java 21), `groupId=com.tfp`, `artifactId=timetracking`.
  - Dependencias main: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `postgresql`, `flyway-core` + `flyway-database-postgresql`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `springdoc-openapi-starter-webmvc-ui:2.8.6`.
  - Dependencias test: `spring-boot-starter-test`, `spring-security-test`, `testcontainers-junit-jupiter`, `testcontainers-postgresql` (BOM `testcontainers-bom:1.20.4`), `archunit-junit5:1.3.0`.
  - Plugin `jacoco-maven-plugin:0.8.12` con `prepare-agent`, `report` y dos ejecuciones `check` (fase `verify`) para los umbrales de dominio (≥90 %, filtro `*.domain.*`) y aplicación (≥80 %, filtro `*.application.*`).
- Estructura de paquetes Clean Architecture bajo `backend/src/main/java/com/tfp/timetracking/` para los 8 módulos (`identity`, `tenant`, `timetracking`, `corrections`, `reporting`, `audit`, `outbox`, `shared`), cada uno con `domain/`, `application/`, `infrastructure/`, `interfaces/rest/`, materializados con `package-info.java` (32 ficheros) para que existan en el árbol aunque estén vacíos.
- `shared.domain`: `DomainException` (excepción base con `errorCode`, sin dependencias de Spring/JPA), `Clock` (puerto, `Instant now()`), `IdGenerator` (puerto, `UUID newId()`).
- `shared.infrastructure`: `SystemClock` (implementación de `Clock` con `Instant.now()`), `RandomUuidGenerator` (implementación de `IdGenerator` con `UUID.randomUUID()`), `SecurityConfig` (única `SecurityFilterChain`: permite `/actuator/health` y `/actuator/health/**`, `denyAll()` para el resto; CSRF y sesiones desactivadas; sin `httpBasic`/`formLogin`).
- `TimetrackingApplication` (clase `@SpringBootApplication`, paquete raíz `com.tfp.timetracking`).
- `application.yml` (config común: JPA `ddl-auto: validate`, Flyway habilitado, actuator solo expone `health` sin detalle, springdoc con rutas por defecto), `application-local.yml` (datasource desde `DB_URL`/`DB_USER`/`DB_PASSWORD` con valores por defecto solo para desarrollo local, sin secretos reales) y `application-test.yml` (perfil `test`; el datasource real lo inyecta Testcontainers vía `@DynamicPropertySource` en el smoke test).
- `db/migration/V1__baseline.sql`: migración Flyway vacía (solo comentario), tal y como indica "Fuera de alcance" de la ficha.
- `backend/src/test/java/com/tfp/timetracking/ApplicationSmokeTest.java`: test de humo `@SpringBootTest` + `@Testcontainers` con contenedor `postgres:16-alpine`, propiedades de datasource inyectadas dinámicamente, verifica que el contexto arranca y que el bean `securityFilterChain` existe.
- `backend/.env.example`: plantilla de variables de entorno (`SPRING_PROFILES_ACTIVE`, `DB_URL`, `DB_USER`, `DB_PASSWORD`) sin valores reales.

### Pruebas (comandos ejecutados y resultado)
```
cd backend && mvn -B clean verify
```
Resultado: **BUILD SUCCESS**. `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` (smoke test con Testcontainers PostgreSQL real, Flyway aplica `V1__baseline` correctamente, contexto Spring arranca en perfil `test`).

### Cobertura
JaCoCo genera el informe (`target/site/jacoco/`) y ejecuta las dos reglas `check` (dominio ≥90 %, aplicación ≥80 %) en fase `verify`: **"All coverage checks have been met."** para ambas.

Motivo por el que no fallan estando `domain`/`application` vacíos (documentado también como comentario en el `pom.xml`, junto a las reglas): las reglas usan `<element>PACKAGE</element>` con `<includes><include>*.domain.*</include></includes>` (resp. `*.application.*`). En este scaffolding esos paquetes solo contienen `package-info.java`, que no genera bytecode ni clases instrumentables; JaCoCo, al no encontrar ningún elemento que cumpla el filtro, no emite violación (no hay "0/0" que falle) y el `check` pasa. En cuanto una tarea futura añada clases reales a `domain`/`application`, estas mismas reglas empezarán a exigir de verdad el 90 %/80 % de cobertura de línea — no hace falta tocar el `pom.xml` para activarlas.

### Seguridad
- Sin secretos en el repositorio: `application-local.yml` usa `${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}` (con defaults de desarrollo local, no productivos); `.env.example` documenta las variables sin valores reales.
- `/actuator/health` es el único endpoint accesible sin autenticación; todo lo demás queda denegado por defecto (`anyRequest().denyAll()`) hasta que el módulo `identity` (fuera de alcance de esta tarea) implemente JWT.
- No se ha añadido `spring-boot-starter-oauth2-resource-server` en esta tarea (ver "Desviaciones" más abajo).
- Aviso esperado en el log de arranque ("Using generated security password...") generado por el auto-configurador de Spring Security al no haber `UserDetailsService` real todavía; es inocuo en este scaffolding y desaparecerá cuando `identity` configure la autenticación JWT.

### Documentación actualizada
Ninguna fuera de este informe: la tarea es scaffolding de código, no toca `docs/`. La ficha no pide actualizar OpenAPI (no hay endpoints de negocio) ni catálogo de eventos.

### ADR
No aplica: T101 no tomó decisiones nuevas fuera de las ya fijadas en CONTEXT-GLOBAL §3 (Maven, paquete base, módulos/capas). Los ADR correspondientes ya existen (T105, `ADR-0003-maven-paquete-base-estructura-modulos.md`).

### Riesgos detectados
- Los umbrales de cobertura JaCoCo son "silenciosamente inactivos" mientras `domain`/`application` estén vacíos; si una tarea futura añade código a esos paquetes sin tests, el build fallará correctamente en `verify`, pero conviene que el equipo lo tenga presente para no interpretar un `BUILD SUCCESS` temprano como "cobertura ya validada".
- `spring-boot-starter-parent 3.5.9` fue elegida en vez de una versión "3.5.0" porque `start.spring.io` ya no sirve líneas 3.x (solo Boot ≥4.0.0 en el entorno actual); se verificó contra Maven Central que 3.5.9 es la última estable de la serie 3.5 y sigue dentro de "Spring Boot 3.x estable" pedido por el orquestador.

### Pendientes / decisiones que necesitan humano
Ninguna decisión bloqueante. Desviaciones menores respecto a la ficha, todas dentro de "Fuera de alcance" o de higiene de scope, por si el humano quiere confirmarlas:
1. No se añadió `spring-boot-starter-oauth2-resource-server` (sí mencionado en CONTEXT-GLOBAL §2/§3 para JWT) porque la ficha T101 solo lista `web, security, data-jpa, validation, postgresql, flyway, springdoc-openapi, actuator` como dependencias de esta tarea, y "seguridad JWT" está explícitamente en "Fuera de alcance". Se añadirá en la tarea del módulo `identity` que implemente la autenticación.
2. Se creó `shared.infrastructure.SecurityConfig` (no mencionado literalmente en "Ficheros previstos") para cumplir el criterio de la ficha "endpoint actuator `/actuator/health` expuesto; el resto cerrado" — sin esta clase, `spring-boot-starter-security` habría bloqueado también `/actuator/health` por defecto.
3. Versión exacta de Spring Boot: 3.5.9 (la más reciente estable de la serie 3.5.x disponible en Maven Central en la fecha de ejecución), en vez de "3.5.0" citado como ejemplo por el orquestador.
