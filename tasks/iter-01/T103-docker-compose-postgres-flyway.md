# T103 — Docker Compose, PostgreSQL y Flyway operativos

- Iteración: 1 · Depende de: T101 · Contexto: CONTEXT-GLOBAL

## Objetivo
Levantar el entorno local completo con Docker Compose y dejar Flyway ejecutando migraciones al arrancar.

## Detalle
1. `docker-compose.yml` en raíz: servicio `postgres` (imagen oficial, volumen, healthcheck) y servicio `backend` (build de `backend/Dockerfile`, depende de postgres healthy). Credenciales SOLO por variables de entorno con `.env.example` en raíz (nunca `.env` commiteado).
2. `backend/Dockerfile` multi-stage (build Maven → runtime JRE 21, usuario no root).
3. Migración `V1__baseline.sql` (puede ser tabla de control vacía o primera tabla real si T201 aún no existe: dejarla mínima y documentada).
4. Perfil `local` del backend apuntando al postgres del compose.
5. Documentar arranque en `README.md` raíz: `docker compose up` + URLs.

## Fuera de alcance
Servicio frontend en compose (llega en T1001), CI (T104).

## Criterios de aceptación
- `docker compose up` levanta postgres + backend; `/actuator/health` responde UP; Flyway aplica migraciones (visible en logs y tabla `flyway_schema_history`).
- Sin secretos en el repo.

## Ficheros previstos
`docker-compose.yml`, `.env.example`, `backend/Dockerfile`, `backend/src/main/resources/db/migration/V1__baseline.sql`, `README.md`.
