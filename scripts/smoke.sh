#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
  printf 'Falta .env. Crea uno con: cp .env.example .env\n' >&2
  exit 1
fi

# Construye desde fuente a proposito: el smoke debe ejercitar el arbol de
# trabajo, no la ultima imagen publicada. Con COMPOSE_FILE=docker-compose.yml se
# puede apuntar a las imagenes de GHCR para comprobar una release ya publicada.
export COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.local.yml}"

docker compose up -d --build

for _ in $(seq 1 60); do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null; then
    break
  fi
  sleep 2
done

curl -fsS http://localhost:8080/actuator/health >/dev/null
curl -fsS http://localhost:4200/ >/dev/null

# Escritura real de extremo a extremo: una solicitud de alta pública. Responde
# 202 y no crea el tenant todavía —eso lo decide plataforma al aprobarla—, pero
# atraviesa API, base de datos y outbox, que es lo que el smoke debe comprobar.
# Requiere PUBLIC_REGISTRATION_ENABLED=true (por defecto está habilitado).
suffix="$(date +%s)"
payload="{\"companyName\":\"Smoke Demo ${suffix}\",\"timezone\":\"Europe/Madrid\",\"firstName\":\"Smoke\",\"lastName\":\"Demo\",\"email\":\"smoke+${suffix}@acme.test\",\"password\":\"supersecretpwd\",\"acceptTerms\":true}"

curl -fsS -X POST \
  -H 'Content-Type: application/json' \
  -d "$payload" \
  http://localhost:8080/api/v1/public/tenant-registrations >/dev/null

printf 'Smoke test completado correctamente.\n'
