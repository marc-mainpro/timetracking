# TFP — MVP control horario

[![CI](https://github.com/OWNER/REPO/actions/workflows/ci.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/ci.yml)

> El badge usa el placeholder `OWNER/REPO`: sustitúyelo por el owner/repo reales en cuanto se cree el remote de GitHub.

MVP SaaS multitenant de control horario (fichajes, correcciones, informes, auditoría y eventos vía Transactional Outbox). Monolito modular. Ver `SDD-MVP-control-horario.md` para el diseño completo.

## Arranque local con Docker Compose

Requisitos: Docker y Docker Compose.

1. Copiar el fichero de variables de entorno de ejemplo:

   ```bash
   cp .env.example .env
   ```

   Editar `.env` si se quieren credenciales distintas de las de ejemplo (nunca commitear `.env`).

2. Levantar los servicios (Postgres + backend):

   ```bash
   docker compose up -d --build
   ```

   El servicio `postgres` expone el puerto `5432` y tiene un healthcheck (`pg_isready`). El servicio `backend` espera a que `postgres` esté healthy antes de arrancar, construye la imagen desde `backend/Dockerfile` (multi-stage: Maven → JRE 21) y ejecuta las migraciones Flyway (`backend/src/main/resources/db/migration`) al iniciar.

3. Comprobar que el backend está arriba:

   ```bash
   curl http://localhost:8080/actuator/health
   ```

   Debe responder `{"status":"UP"}`.

4. URLs disponibles:
   - API: http://localhost:8080
   - Health: http://localhost:8080/actuator/health
   - OpenAPI JSON: http://localhost:8080/v3/api-docs
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - Postgres: `localhost:5432` (credenciales según `.env`)

5. Ver logs de arranque y de Flyway:

   ```bash
   docker compose logs backend
   ```

6. Parar y limpiar (incluye el volumen de datos de Postgres):

   ```bash
   docker compose down -v
   ```

## Estructura del repo

- `backend/` — API Spring Boot (Java 21, Maven). Ver `backend/.env.example` para variables usadas fuera de Docker (perfil `local` directo con `mvn spring-boot:run`).
- `frontend/` — Aplicación Angular (aún no incluida en `docker-compose.yml`; llega en una iteración posterior).
- `docs/` — Documentación adicional.
- `tasks/` — Fichas de tareas y contexto de ejecución.

## CI

`.github/workflows/ci.yml` ejecuta en cada `push` a `main` y en cada `pull_request`:

- **backend**: JDK 21, `mvn -B verify` (tests, ArchUnit, umbrales de cobertura JaCoCo con Testcontainers PostgreSQL) y publica el informe JaCoCo como artefacto.
- **frontend**: Node 20, `npm ci`, `ng lint`, `ng test --watch=false --browsers=ChromeHeadless`, `ng build`.

## Fuera de alcance de este compose

- Servicio `frontend` (se añadirá en una tarea posterior).
