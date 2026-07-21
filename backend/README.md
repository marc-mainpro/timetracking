# Backend — API de control horario

API REST del MVP SaaS multitenant de control horario. Monolito modular en
**Spring Boot 3.5 / Java 21** con **PostgreSQL** y **Flyway**, autenticación
**JWT** (access token + refresh cookie) y **Transactional Outbox** para los
eventos de integración.

> Contexto general del producto y arranque de todo el stack: [`../README.md`](../README.md).
> Diseño detallado: [`../SDD-MVP-control-horario.md`](../SDD-MVP-control-horario.md) y [`../docs/`](../docs).

## Stack

- Java 21, Spring Boot 3.5.9 (Web, Data JPA, Security, OAuth2 Resource Server, Validation, Actuator).
- PostgreSQL + Flyway (migraciones versionadas, `ddl-auto: validate`).
- springdoc-openapi (Swagger UI / OpenAPI).
- Bucket4j (rate limiting), Micrometer/Actuator (health y métricas).
- Testcontainers + JaCoCo para tests de integración y cobertura.

## Arquitectura

Monolito modular con separación estricta **dominio / aplicación /
infraestructura / interfaces** por módulo de negocio. Cada módulo bajo
`com.tfp.timetracking`:

| Módulo | Responsabilidad |
| --- | --- |
| `identity` | Autenticación (login/refresh/logout), usuarios y empleados, emisión de JWT. |
| `tenant` | Registro de organización (tenant) y su administrador inicial. |
| `timetracking` | Jornadas y pausas (inicio/fin, workday del empleado y vista admin). |
| `corrections` | Solicitudes de corrección de jornada y su aprobación/rechazo. |
| `reporting` | Informes de tiempo trabajado (empleado y tenant) y exportación CSV. |
| `audit` | Eventos de auditoría de acciones sensibles. |
| `outbox` | Transactional Outbox: persistencia y publicación de eventos de integración. |
| `shared` | Seguridad transversal (filtros, contexto de tenant, CORS, rate limit), utilidades y manejo de errores. |

### Multitenancy

El `tenantId` se deriva del principal autenticado (claim del JWT), no de la
petición. Todas las consultas se acotan por tenant, garantizando aislamiento de
datos entre organizaciones.

### Autenticación

- `POST /api/v1/auth/login` devuelve un **access token** (JWT, claim `roles`
  con `TENANT_ADMIN` / `EMPLOYEE`) y fija una **refresh cookie** `HttpOnly`.
- `POST /api/v1/auth/refresh` rota el refresh token; `POST /api/v1/auth/logout`
  lo revoca.
- Autorización por rol con `@PreAuthorize("hasRole('TENANT_ADMIN')")` etc.

## Endpoints principales

Base: `/api/v1`. Documentación viva en Swagger UI (ver abajo).

| Prefijo | Módulo | Notas |
| --- | --- | --- |
| `/auth` | identity | login, refresh, logout. |
| `/auth/register` | tenant | alta de organización + admin. |
| `/employees` | identity | gestión de empleados (admin). |
| `/workdays` | timetracking | jornada del empleado y su historial. |
| `/admin/workdays` | timetracking | vista de jornadas del admin. |
| `/corrections` | corrections | solicitudes y resolución de correcciones. |
| `/reports` | reporting | informes y exportación CSV. |
| `/admin/audit-events` | audit | consulta de auditoría (admin). |

## Requisitos

- JDK 21
- Maven 3.9+
- PostgreSQL 16 (o usar el `docker compose` del repo)

No hay Maven Wrapper: se usa el `mvn` del sistema.

## Configuración

Perfiles Spring en `src/main/resources`:

- `application.yml` — configuración base (lee variables de entorno).
- `application-local.yml` — desarrollo local.
- `application-test.yml` — tests (scheduler de outbox desactivado, etc.).

Variables de entorno relevantes (ver [`../.env.example`](../.env.example)):

| Variable | Descripción |
| --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión a PostgreSQL. |
| `JWT_SECRET` | Secreto de firma HS256 (mínimo 32 bytes). |
| `FRONTEND_ORIGIN` | Origen permitido para CORS. |
| `AUTH_REFRESH_COOKIE_SECURE` | `true` en producción (cookie solo por HTTPS). |
| `APP_REQUEST_MAX_PAYLOAD_BYTES` | Límite de tamaño de petición. |

## Ejecución local

Con PostgreSQL disponible y las variables de entorno exportadas:

```bash
mvn spring-boot:run
```

O empaquetar y ejecutar el jar:

```bash
mvn -B clean package
java -jar target/*.jar
```

La forma recomendada de levantarlo junto a Postgres y el frontend es el
`docker compose` de la raíz del repo.

## Migraciones de base de datos

Flyway aplica automáticamente las migraciones de
`src/main/resources/db/migration` (`V1__baseline.sql` … `V9__processed_event.sql`)
al arrancar. Con `ddl-auto: validate`, el esquema JPA debe coincidir con el
migrado; **cualquier cambio de esquema va como nueva migración `Vn__…`**, nunca
por autogeneración.

## Tests y calidad

```bash
mvn -B verify          # tests unitarios + integración + E2E de API + JaCoCo
```

- Integración con **Testcontainers** (PostgreSQL real).
- E2E de API de alto nivel: `EndToEndFlowIT` recorre el flujo completo
  (registro → login → empleado → jornada → pausas → corrección → aprobación →
  auditoría → outbox) y verifica aislamiento entre tenants.
- Cobertura con JaCoCo (informe en `target/site/jacoco`).

## Observabilidad y documentación

Con el servicio arriba en `http://localhost:8080`:

- Health: `/actuator/health`
- OpenAPI JSON: `/v3/api-docs` · YAML: `/v3/api-docs.yaml`
- Swagger UI: `/swagger-ui.html`

## Imagen Docker

`Dockerfile` multi-stage (build con Maven + runtime JRE). Se construye desde la
raíz con `docker compose build backend`.
