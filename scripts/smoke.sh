#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
  printf 'Falta .env. Crea uno con: cp .env.example .env\n' >&2
  exit 1
fi

docker compose up -d --build

for _ in $(seq 1 60); do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null; then
    break
  fi
  sleep 2
done

curl -fsS http://localhost:8080/actuator/health >/dev/null
curl -fsS http://localhost:4200/ >/dev/null

suffix="$(date +%s)"
payload="{\"tenantName\":\"Smoke Demo ${suffix}\",\"timezone\":\"Europe/Madrid\",\"adminEmail\":\"smoke+${suffix}@acme.test\",\"adminPassword\":\"supersecretpwd\",\"firstName\":\"Smoke\",\"lastName\":\"Demo\"}"

curl -fsS -X POST \
  -H 'Content-Type: application/json' \
  -d "$payload" \
  http://localhost:8080/api/v1/auth/register >/dev/null

printf 'Smoke test completado correctamente.\n'
