# TFP — MVP control horario

[![CI](https://github.com/OWNER/REPO/actions/workflows/ci.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/ci.yml)

> El badge usa el placeholder `OWNER/REPO`: sustitúyelo por el owner/repo reales en cuanto se cree el remote de GitHub.

MVP SaaS multitenant de control horario con Spring Boot, Angular y PostgreSQL.
Incluye registro de tenant, autenticación JWT con refresh token, gestión de
empleados, fichajes, correcciones, informes, auditoría y Transactional Outbox.

## Arquitectura

- Monolito modular con separación dominio/aplicación/infraestructura.
- Backend: Spring Boot 3.5, Java 21, PostgreSQL, Flyway, Spring Security.
- Frontend: Angular 19 servido por nginx.
- Multitenancy por `tenantId` derivado del principal autenticado.
- Eventos de integración persistidos en outbox transaccional.

Documentación principal:

- Arquitectura: `docs/architecture/`
- Dominio: `docs/domain/`
- API: `docs/api/`
- Seguridad: `docs/security/`
- Testing: `docs/testing/`
- Integración/eventos: `docs/integration/`
- Manuales/demo: `docs/manuals/`, `docs/demo/`

## Arranque local con Docker Compose

Requisitos: Docker y Docker Compose.

1. Copiar el fichero de variables de entorno de ejemplo:

   ```bash
   cp .env.example .env
   ```

   Editar `.env` si se quieren credenciales distintas de las de ejemplo (nunca commitear `.env`).

2. Levantar los servicios (Postgres + backend + frontend):

   ```bash
   docker compose up -d --build
   ```

   `postgres` queda en la red interna, `backend` publica `8080` y `frontend`
   publica `4200`. Ambos servicios HTTP tienen healthcheck.

3. Comprobar que backend y frontend están arriba:

   ```bash
    curl http://localhost:8080/actuator/health
    curl http://localhost:4200/
   ```

    El backend debe responder `{"status":"UP"}` y el frontend debe servir
    `index.html`.

4. URLs disponibles:
   - Frontend: http://localhost:4200
   - API: http://localhost:8080
   - Health: http://localhost:8080/actuator/health
   - OpenAPI JSON: http://localhost:8080/v3/api-docs
   - OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml
   - Swagger UI: http://localhost:8080/swagger-ui.html

5. Smoke test automático:

   ```bash
   ./scripts/smoke.sh
   ```

6. Datos de demo:

   ```bash
   ./scripts/seed-demo.sh
   ```

7. Ver logs de arranque y de Flyway:

   ```bash
   docker compose logs backend
   ```

8. Parar y limpiar (incluye el volumen de datos de Postgres):

   ```bash
   docker compose down -v
   ```

## Estructura del repo

- `backend/` — API Spring Boot.
- `frontend/` — SPA Angular + Dockerfile/nginx.
- `docs/` — Arquitectura, dominio, seguridad, testing, manuales y demo.
- `scripts/` — Smoke test y seed de demo.
- `tasks/` — Fichas de tareas y contexto de ejecución.

## CI

`.github/workflows/ci.yml` ejecuta en cada `push` a `main` y en cada `pull_request`:

- **backend**: `mvn -B verify`, tests de integración, E2E de API y JaCoCo.
- **frontend**: lint, tests con cobertura y build.
- **docker**: build de las imágenes de backend y frontend.

## Demo y operación

- Guion de demo: `docs/demo/demo-script.md`
- Manual de usuario: `docs/manuals/user-guide.md`
- Manual de operación: `docs/manuals/operations.md`
