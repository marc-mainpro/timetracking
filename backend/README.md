# Backend — API de control horario

API REST del MVP SaaS multitenant de control horario. Monolito modular en
**Spring Boot 3.5 / Java 21** con **PostgreSQL** y **Flyway**, autenticación
**JWT** (access token + refresh cookie) y **Transactional Outbox** para los
eventos de integración.

> Contexto general del producto y arranque de todo el stack: [`../README.md`](../README.md).
> Diseño detallado: [`../docs/specs/SDD-MVP-control-horario.md`](../docs/specs/SDD-MVP-control-horario.md) y [`../docs/`](../docs).

## Stack

- Java 21, Spring Boot 3.5.9 (Web, Data JPA, Security, OAuth2 Resource Server, Validation, Actuator).
- PostgreSQL + Flyway (migraciones versionadas, `ddl-auto: validate`).
- springdoc-openapi (Swagger UI / OpenAPI).
- Bucket4j (rate limiting), Micrometer/Actuator (health y métricas).
- PDFBox (exportación PDF de informes).
- Testcontainers + JaCoCo para tests de integración y cobertura, ArchUnit para
  las reglas de arquitectura (todo dentro de `mvn verify`).

## Arquitectura

Monolito modular con separación estricta **dominio / aplicación /
infraestructura / interfaces** por módulo de negocio. Cada módulo bajo
`com.tfp.timetracking`:

| Módulo | Responsabilidad |
| --- | --- |
| `identity` | Autenticación (login/refresh/logout), sesiones, recuperación de contraseña, usuarios y empleados, emisión de JWT. |
| `tenant` | Solicitudes de alta, ciclo de vida del tenant (PENDING/ACTIVE/SUSPENDED/ARCHIVED) y administración de plataforma. |
| `timetracking` | Jornadas y pausas (inicio/fin, workday del empleado y vista admin), reglas horarias y evaluación de la jornada cerrada. |
| `corrections` | Solicitudes de corrección de jornada y su aprobación/rechazo. |
| `absence` | Tipos de ausencia, solicitud, cancelación, aprobación y rechazo. |
| `calendar` | Calendarios laborales con festivos y jornadas especiales, y su resolución por ámbito (tenant, equipo o empleado). |
| `shift` | Plantillas de turno y asignaciones, incluidos los turnos que cruzan medianoche. |
| `reporting` | Informes de tiempo trabajado (empleado y tenant) y exportación CSV y PDF. |
| `notification` | Notificaciones internas y por correo, con destinatarios por rol y política de canal. |
| `audit` | Eventos de auditoría de acciones sensibles, de tenant y de plataforma. |
| `outbox` | Transactional Outbox: persistencia, publicación, reintentos y mantenimiento de las colas fallidas. |
| `shared` | Seguridad transversal (filtros, contexto de tenant, CORS, rate limit), utilidades y manejo de errores. |

### Multitenancy

El `tenantId` se deriva del principal autenticado (claim del JWT), no de la
petición. Todas las consultas se acotan por tenant, garantizando aislamiento de
datos entre organizaciones.

### Autenticación

- `POST /api/v1/auth/login` devuelve un **access token** (JWT, claim `roles`
  con `TENANT_ADMIN` / `EMPLOYEE`, o `PLATFORM_ADMIN` para el rol global) y fija
  una **refresh cookie** `HttpOnly`.
- `POST /api/v1/auth/refresh` rota el refresh token; `POST /api/v1/auth/logout`
  lo revoca.
- Autorización por rol con `@PreAuthorize("hasRole('TENANT_ADMIN')")` etc.
- `PLATFORM_ADMIN` es un rol global: pertenece a un tenant de sistema fijo, no es
  asignable dentro de un tenant ni por registro público, y solo se aprovisiona
  por arranque controlado desde variables de entorno (ADR-0010).

## Endpoints principales

Base: `/api/v1`. Documentación viva en Swagger UI (ver abajo).

| Prefijo | Módulo | Notas |
| --- | --- | --- |
| `/auth` | identity | login, refresh, logout. |
| `/auth/sessions` | identity | listado y revocación de sesiones activas. |
| `/auth/password` | identity | solicitud y confirmación del restablecimiento de contraseña. |
| `/public/tenant-registrations` | tenant | alta pública: solicitud, verificación de correo y reenvío. |
| `/employees` | identity | gestión de empleados (admin). |
| `/workdays` | timetracking | jornada del empleado, pausas e historial. |
| `/admin/workdays` | timetracking | vista de jornadas del admin. |
| `/admin/hourly-rules` | timetracking | consulta y actualización de las reglas horarias del tenant. |
| `/corrections` | corrections | solicitudes y resolución de correcciones. |
| `/app/absences`, `/admin/absences` | absence | solicitud y cancelación por el empleado; aprobación y rechazo por el admin. |
| `/admin/calendars`, `/admin/calendar-assignments` | calendar | calendarios laborales y su asignación por ámbito. |
| `/app/shifts`, `/admin/shifts` | shift | turnos del empleado; plantillas y asignaciones del admin. |
| `/reports` | reporting | informes y exportación CSV y PDF. |
| `/notifications` | notification | bandeja, contador de no leídas y marcado como leída. |
| `/admin/audit-events` | audit | consulta de auditoría (admin). |
| `/platform/tenants` | tenant | ciclo de vida de tenants (`PLATFORM_ADMIN`). |
| `/platform/registrations` | tenant | revisión, aprobación y rechazo de solicitudes de alta. |
| `/platform/audit` | audit | auditoría de plataforma. |
| `/platform/system-status`, `/platform/queues` | outbox | salud de las colas y reintento o descarte de mensajes fallidos. |

## Requisitos

- JDK 21
- Maven 3.9+
- PostgreSQL 16 (o usar el `docker compose` del repo)

No hay Maven Wrapper: se usa el `mvn` del sistema.

## Configuración

Perfiles Spring en `src/main/resources`:

- `application.yml` — configuración base (lee variables de entorno). Importa
  además los `config/*.yml` de cada área como `optional:`, así que la ausencia de
  un fichero no rompe el arranque.
- `application-local.yml` — desarrollo local.
- `application-prod.yml` — datasource de producción (`DB_URL`, o bien
  `DB_HOST` + `DB_PORT` + `DB_NAME`).
- `application-test.yml` — tests (scheduler de outbox desactivado, etc.).

Variables de entorno relevantes (ver [`../.env.example`](../.env.example)):

| Variable | Descripción |
| --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión a PostgreSQL. |
| `JWT_SECRET` | Secreto de firma HS256 (mínimo 32 bytes). |
| `FRONTEND_ORIGIN` | Origen permitido para CORS, y base de la que se derivan los enlaces de los correos. |
| `AUTH_PASSWORD_RESET_URL_TEMPLATE`, `REGISTRATION_VERIFICATION_URL` | Enlaces de los correos de recuperación y de verificación de alta. Un único `%s` para el token. |
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
`docker compose` de la raíz del repo: `docker-compose.yml` usa las imágenes
publicadas en GHCR y `docker-compose.local.yml` las construye desde fuente.

## Migraciones de base de datos

Flyway aplica automáticamente las migraciones de
`src/main/resources/db/migration` (desde `V1__baseline.sql` hasta la última,
hoy `V27__queue_discard.sql`) al arrancar. Con `ddl-auto: validate`, el esquema JPA debe coincidir con el
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
raíz con `docker compose -f docker-compose.local.yml build backend`; el
`docker-compose.yml` de la raíz no compila, descarga la imagen de GHCR.
