#!/usr/bin/env bash
# Restauracion de un backup logico de PostgreSQL (T150-03, RO-007, RT-008).
#
# Implementa el procedimiento de docs/manuals/backup-restore.md:
#   detener aplicacion -> restaurar base -> validar -> arrancar -> smoke tests
#
# El script cubre los cuatro primeros pasos. Los smoke tests se ejecutan aparte
# con scripts/smoke.sh, que ya levanta el stack completo y prueba el flujo.
#
# Uso:
#   scripts/backup/restore-postgres.sh <fichero.dump|fichero.dump.gpg> [--yes]
#
# Variables de entorno:
#   BACKUP_PASSPHRASE  passphrase GPG si el backup esta cifrado
#   POSTGRES_DB, POSTGRES_USER  base y usuario (por defecto timetracking)
#   COMPOSE_POSTGRES_SERVICE    servicio de compose (por defecto postgres)
#
# Codigos de salida:
#   0  restauracion completada y validada
#   1  fallo de la restauracion o de la validacion
#   2  error de configuracion, argumentos o requisitos previos
#
# ATENCION: la restauracion es DESTRUCTIVA. Elimina y recrea la base de datos
# destino. Pide confirmacion explicita salvo que se pase --yes.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

BACKUP_FILE="${1:-}"
ASSUME_YES=0
shift || true
for arg in "$@"; do
  case "$arg" in
    --yes | -y) ASSUME_YES=1 ;;
    *)
      printf 'Argumento desconocido: %s\n' "$arg" >&2
      exit 2
      ;;
  esac
done

if [[ -z $BACKUP_FILE ]]; then
  sed -n '2,28p' "${BASH_SOURCE[0]}"
  exit 2
fi
[[ -f $BACKUP_FILE ]] || {
  printf 'No existe el backup: %s\n' "$BACKUP_FILE" >&2
  exit 2
}

if [[ -f .env ]]; then
  while IFS='=' read -r key value; do
    case "$key" in
      POSTGRES_DB | POSTGRES_USER | BACKUP_PASSPHRASE)
        [[ -n ${!key-} ]] || printf -v "$key" '%s' "$value"
        ;;
    esac
  done < <(grep -E '^[A-Z_]+=' .env || true)
fi

POSTGRES_DB="${POSTGRES_DB:-timetracking}"
POSTGRES_USER="${POSTGRES_USER:-timetracking}"
COMPOSE_POSTGRES_SERVICE="${COMPOSE_POSTGRES_SERVICE:-postgres}"

LOG_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/restore-$(date -u +%Y%m%dT%H%M%SZ).log"

log() { printf '%s [restore] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$LOG_FILE"; }
err() { printf '%s [restore] ERROR: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$LOG_FILE" >&2; }

command -v docker >/dev/null 2>&1 || {
  err 'docker no esta disponible en el PATH'
  exit 2
}

START_TS=$(date +%s)
log "inicio | backup=$BACKUP_FILE base=$POSTGRES_DB"

# --- 0. Verificacion de integridad del backup --------------------------------

if [[ -f "$BACKUP_FILE.sha256" ]]; then
  if sha256sum --check --status "$BACKUP_FILE.sha256"; then
    log 'sha256 verificado'
  else
    err 'el sha256 del backup NO coincide; backup corrupto o manipulado'
    exit 1
  fi
else
  log 'AVISO: no hay fichero .sha256 junto al backup; no se verifica la integridad'
fi

if [[ $ASSUME_YES -eq 0 ]]; then
  printf 'Se va a ELIMINAR y recrear la base "%s" con el contenido de %s.\n' \
    "$POSTGRES_DB" "$BACKUP_FILE"
  read -r -p 'Escribe RESTAURAR para continuar: ' confirm
  [[ $confirm == 'RESTAURAR' ]] || {
    err 'cancelado por el operador'
    exit 2
  }
fi

# --- 1. Descifrado (si procede) ----------------------------------------------

WORK_DIR="$(mktemp -d)"
cleanup() {
  # El volcado en claro es un fichero con datos personales: se borra siempre,
  # incluso si la restauracion falla.
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

PLAIN_DUMP="$WORK_DIR/restore.dump"
if [[ $BACKUP_FILE == *.gpg ]]; then
  [[ -n ${BACKUP_PASSPHRASE:-} ]] || {
    err 'el backup esta cifrado y falta BACKUP_PASSPHRASE'
    exit 2
  }
  command -v gpg >/dev/null 2>&1 || {
    err 'gpg no esta disponible en el PATH'
    exit 2
  }
  if ! printf '%s' "$BACKUP_PASSPHRASE" | gpg --batch --yes --quiet \
    --passphrase-fd 0 --pinentry-mode loopback \
    --decrypt --output "$PLAIN_DUMP" "$BACKUP_FILE" 2>>"$LOG_FILE"; then
    err 'el descifrado GPG fallo (passphrase incorrecta o fichero corrupto)'
    exit 1
  fi
  log 'backup descifrado'
else
  cp "$BACKUP_FILE" "$PLAIN_DUMP"
  log 'backup sin cifrar copiado al area de trabajo'
fi

# --- 2. Detener la aplicacion ------------------------------------------------
#
# El backend debe parar ANTES de tocar la base: si sigue vivo mantiene
# conexiones abiertas que impiden hacer DROP DATABASE y, peor, puede escribir
# sobre la base a medio restaurar.

log 'deteniendo backend y frontend'
docker compose stop backend frontend >>"$LOG_FILE" 2>&1 || true

if ! docker compose ps --status running --services 2>/dev/null | grep -qx "$COMPOSE_POSTGRES_SERVICE"; then
  log "levantando el servicio $COMPOSE_POSTGRES_SERVICE"
  docker compose up -d "$COMPOSE_POSTGRES_SERVICE" >>"$LOG_FILE" 2>&1
  for _ in $(seq 1 30); do
    if docker compose exec -T "$COMPOSE_POSTGRES_SERVICE" \
      pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done
fi

# --- 3. Restaurar ------------------------------------------------------------
#
# Se recrea la base desde cero contra `postgres` para no arrastrar objetos
# huerfanos de la base anterior. Antes hay que expulsar cualquier conexion
# residual, o el DROP DATABASE fallara.

log 'cerrando conexiones residuales a la base destino'
docker compose exec -T "$COMPOSE_POSTGRES_SERVICE" psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$POSTGRES_DB' AND pid <> pg_backend_pid();" \
  >>"$LOG_FILE" 2>&1 || true

log 'recreando la base destino'
if ! docker compose exec -T "$COMPOSE_POSTGRES_SERVICE" psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS \"$POSTGRES_DB\";" \
  -c "CREATE DATABASE \"$POSTGRES_DB\" OWNER \"$POSTGRES_USER\";" \
  >>"$LOG_FILE" 2>&1; then
  err 'no se pudo recrear la base destino'
  exit 1
fi

log 'restaurando el volcado'
if ! docker compose exec -T "$COMPOSE_POSTGRES_SERVICE" \
  pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges --exit-on-error \
  <"$PLAIN_DUMP" >>"$LOG_FILE" 2>&1; then
  err 'pg_restore fallo; la base queda en estado inconsistente. Revisa el log'
  exit 1
fi
log 'pg_restore completado sin errores'

# --- 4. Validar --------------------------------------------------------------
#
# Validacion minima antes de dejar entrar trafico: el esquema esta completo,
# Flyway no ve migraciones pendientes ni fallidas y hay datos de negocio.

validate_sql() {
  docker compose exec -T "$COMPOSE_POSTGRES_SERVICE" \
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "$1" 2>>"$LOG_FILE"
}

tables=$(validate_sql "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';")
log "validacion | tablas en public: ${tables:-0}"
if [[ ${tables:-0} -lt 1 ]]; then
  err 'la base restaurada no tiene tablas'
  exit 1
fi

failed_migrations=$(validate_sql "SELECT count(*) FROM flyway_schema_history WHERE success = false;" || echo '')
if [[ -n $failed_migrations && $failed_migrations != '0' ]]; then
  err "hay $failed_migrations migraciones Flyway marcadas como fallidas"
  exit 1
fi
applied=$(validate_sql "SELECT count(*) FROM flyway_schema_history WHERE success = true;" || echo '0')
log "validacion | migraciones Flyway aplicadas: ${applied:-0}"

for t in tenant app_user; do
  exists=$(validate_sql "SELECT to_regclass('public.$t') IS NOT NULL;")
  if [[ $exists != 't' ]]; then
    err "falta la tabla esperada '$t' en la base restaurada"
    exit 1
  fi
  rows=$(validate_sql "SELECT count(*) FROM $t;")
  log "validacion | $t: $rows filas"
done

# --- 5. Arrancar la aplicacion ----------------------------------------------

log 'arrancando backend y frontend'
docker compose up -d backend frontend >>"$LOG_FILE" 2>&1

log 'esperando a que el backend responda /actuator/health'
healthy=0
for _ in $(seq 1 60); do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 2
done
if [[ $healthy -ne 1 ]]; then
  err 'el backend no llego a estar healthy tras la restauracion'
  exit 1
fi

ELAPSED=$(($(date +%s) - START_TS))
log "fin | restauracion correcta en ${ELAPSED}s | log=$LOG_FILE"
log 'siguiente paso (T150-04): ejecutar scripts/smoke.sh y anotar el resultado'
exit 0
