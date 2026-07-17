## T103 — Docker Compose, PostgreSQL y Flyway operativos

### Cambios
- `docker-compose.yml` (raíz, nuevo): servicios `postgres` (imagen `postgres:16-alpine`, volumen nombrado `postgres_data`, healthcheck `pg_isready`) y `backend` (build de `backend/Dockerfile`, `depends_on: postgres` con `condition: service_healthy`). Variables de credenciales tomadas de `.env` (via `${POSTGRES_DB}`, `${POSTGRES_USER}`, `${POSTGRES_PASSWORD}` con valores por defecto solo de desarrollo), inyectadas al backend como `DB_URL`/`DB_USER`/`DB_PASSWORD` y `SPRING_PROFILES_ACTIVE=local`. Puertos publicados: `5432` (postgres) y `8080` (backend).
- `backend/Dockerfile` (nuevo): multi-stage — build con `maven:3.9-eclipse-temurin-21` (`dependency:go-offline` cacheado + `mvn package -DskipTests`) y runtime con `eclipse-temurin:21-jre-jammy`, usuario no root (`spring:spring`), `ENTRYPOINT java -jar app.jar`, puerto 8080 expuesto.
- `.env.example` (raíz, nuevo): plantilla con `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`. No se commitea `.env` (ya cubierto por `.gitignore` existente).
- `README.md` (raíz, nuevo): instrucciones de arranque (`cp .env.example .env`, `docker compose up -d --build`), URLs (health, OpenAPI, Swagger UI, Postgres), cómo ver logs de Flyway y cómo parar (`docker compose down -v`), y nota explícita de fuera de alcance (frontend en compose, CI).
- No fue necesario tocar `backend/src/main/resources/**`: el perfil `local` ya existente (`application-local.yml`, variables `DB_URL`/`DB_USER`/`DB_PASSWORD`) funciona sin cambios apuntando al servicio `postgres` del compose (host `postgres`, puerto `5432` dentro de la red del compose).
- No se ha tocado `V1__baseline.sql` (ya existía de T101, mínima y documentada, cumple lo pedido en el criterio "puede ser tabla de control vacía").

### Pruebas (comandos ejecutados y resultado)
1. `cp .env.example .env`
2. `docker compose up -d --build`
   - Build de imagen `p1-backend` correcto (multi-stage, sin errores).
   - `p1-postgres-1` → `Started` → `Healthy`.
   - `p1-backend-1` → `Started` (esperó a `postgres` healthy antes de arrancar, según `depends_on`).
3. `curl -s -o /tmp/health.json -w "%{http_code}" http://localhost:8080/actuator/health` (con reintentos hasta 200) →
   - **`HTTP 200`**, body `{"status":"UP"}`.
4. `docker compose logs backend` → confirma en logs:
   - `Database: jdbc:postgresql://postgres:5432/timetracking (PostgreSQL 16.14)`
   - `Schema history table "public"."flyway_schema_history" does not exist yet`
   - `Successfully validated 1 migration`
   - `Migrating schema "public" to version "1 - baseline"`
   - `Successfully applied 1 migration to schema "public", now at version v1`
   - `Started TimetrackingApplication in 4.158 seconds`
5. `docker compose exec postgres psql -U timetracking -d timetracking -c "\dt"` → tabla `flyway_schema_history` presente.
   `select version, description, success from flyway_schema_history;` → fila `1 | baseline | t` (éxito).
6. `docker compose ps` → `postgres` `Up (healthy)`, `backend` `Up`, puertos `5432` y `8080` publicados correctamente.
7. `docker compose down -v` → contenedores, volumen `p1_postgres_data` y red `p1_default` eliminados correctamente (verificado con `docker compose ps -a` y `docker volume ls`).
8. Se eliminó el `.env` local generado en el paso 1 (no se commitea; ya cubierto por `.gitignore`).

Resultado: **todos los criterios de aceptación de la ficha se cumplen** — `docker compose up` levanta postgres + backend, `/actuator/health` responde UP, Flyway aplica migraciones (visible en logs y en `flyway_schema_history`), sin secretos en el repo (solo `.env.example` con valores de ejemplo `changeme`).

### Cobertura
No aplica — tarea de infraestructura (Docker/Compose), sin código de dominio/aplicación nuevo. No se ejecutó `mvn verify` porque no se ha modificado código Java ni migraciones; el build de Maven dentro del Dockerfile (`mvn package -DskipTests`) se ejecutó correctamente como parte de la construcción de la imagen.

### Seguridad
- Credenciales solo por variables de entorno; `.env` no se commitea (regla ya presente en `.gitignore` raíz).
- `.env.example` contiene únicamente valores de ejemplo no sensibles (`changeme`).
- Imagen de runtime del backend corre con usuario no root (`spring:spring`).
- No se han expuesto puertos adicionales ni endpoints más allá de `health` (ya configurado en T101).

### Documentación actualizada
- `README.md` (raíz) creado con instrucciones de arranque, URLs y comandos de parada, según lo pedido en la ficha.

### ADR
Ninguna decisión nueva fuera de las ya fijadas en CONTEXT-GLOBAL/SDD (imagen Postgres, estructura del Dockerfile multi-stage y nombres de variables de entorno son implementación directa de decisiones ya tomadas, no requieren ADR).

### Riesgos detectados
- El servicio `backend` no tiene healthcheck propio en el compose (no exigido por la ficha, que solo pide healthcheck en `postgres` y `depends_on: service_healthy`); si en el futuro otro servicio necesitara esperar a que el backend esté listo, habría que añadirlo.
- `postgres` y `backend` publican puertos fijos (`5432`, `8080`) en el host; en un entorno con esos puertos ocupados el `docker compose up` fallaría (aceptable para entorno local de desarrollo, fuera del alcance de esta tarea cambiarlo).

### Pendientes / decisiones que necesitan humano
Ninguno. Tarea completada sin ampliar alcance (sin servicio frontend en compose, sin CI, según lo indicado en "Fuera de alcance" de la ficha).
