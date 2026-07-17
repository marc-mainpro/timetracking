# T1001 — Dockerización final y despliegue

- Iteración: 10 · Depende de: T903 · Contexto: CONTEXT-GLOBAL §2-3

## Objetivo
Sistema completo arrancable con un solo `docker compose up`, listo para demo/despliegue.

## Detalle
1. `frontend/Dockerfile` multi-stage (build Angular → nginx no root) con configuración nginx: servir SPA (fallback a index.html), proxy `/api` al backend, cabeceras de seguridad, gzip.
2. `docker-compose.yml` completo: postgres + backend + frontend, healthchecks encadenados, red interna (postgres no expuesto al host por defecto), variables por `.env` (`.env.example` completo y documentado: JWT_SECRET, credenciales BD, CORS origin, etc.).
3. `docker-compose.prod.yml` (override) si hay diferencias local/producción (TLS termina fuera; documentar supuesto).
4. CI ampliado: build de ambas imágenes en el pipeline (sin push obligatorio; si se publica, a GHCR con tag por SHA).
5. Smoke test documentado y scripteado (`scripts/smoke.sh`): compose up → health backend → frontend responde → registro de tenant demo por API.

## Criterios de aceptación
- Desde un clon limpio: `cp .env.example .env` (+ valores) y `docker compose up` deja el sistema usable en el navegador; CI verde con builds de imagen.

## Ficheros previstos
`frontend/Dockerfile`, `frontend/nginx.conf`, `docker-compose*.yml`, `.env.example`, `scripts/smoke.sh`, `.github/workflows/ci.yml`.
