# T101 — Scaffolding del backend Spring Boot

- Iteración: 1 · Depende de: — · Contexto: CONTEXT-GLOBAL

## Objetivo
Crear el proyecto backend Maven con estructura Clean Architecture modular, compilable y con un test de humo verde.

## Detalle
1. Crear `backend/` con Maven, Java 21, Spring Boot 3.x. Dependencias: web, security, data-jpa, validation, postgresql, flyway, springdoc-openapi, actuator; test: JUnit 5, AssertJ, Mockito, Testcontainers (postgresql), ArchUnit; plugin JaCoCo con umbrales (dominio 90 %, aplicación 80 % — configurar por paquete `**.domain.**` y `**.application.**`).
2. Paquete base `com.tfp.timetracking` con módulos: `identity`, `tenant`, `timetracking`, `corrections`, `reporting`, `audit`, `outbox`, `shared`. En cada módulo, subpaquetes `domain`, `application`, `infrastructure`, `interfaces.rest` (crear con `package-info.java` para que existan).
3. `shared`: `DomainException` base, generador de UUID, `Clock` inyectable (puerto en dominio, implementación en infraestructura).
4. `application.yml` con perfiles `local` y `test`; datasource por variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`); sin secretos hardcodeados.
5. Endpoint actuator `/actuator/health` expuesto; el resto cerrado.
6. Test de humo: contexto arranca (usar Testcontainers PostgreSQL).

## Fuera de alcance
Entidades de negocio, seguridad JWT, migraciones de negocio (solo Flyway configurado, sin scripts o con `V1__baseline.sql` vacío).

## Criterios de aceptación
- `mvn verify` en verde con Docker disponible.
- Estructura de paquetes creada según CONTEXT-GLOBAL §3.
- Ningún secreto en el repositorio; existe `backend/.env.example`.

## Ficheros previstos
`backend/pom.xml`, `backend/src/main/java/com/tfp/timetracking/**`, `backend/src/main/resources/application*.yml`, `backend/src/test/java/**/ApplicationSmokeTest.java`, `backend/.env.example`.
